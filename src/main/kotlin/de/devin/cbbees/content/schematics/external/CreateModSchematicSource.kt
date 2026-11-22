package de.devin.cbbees.content.schematics.external

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.simibubi.create.CreateClient
import de.devin.cbbees.CreateBuzzyBeez
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of [ExternalSchematicSource] backed by createmod.com.
 *
 * All endpoints use HMAC-SHA256 signature authentication:
 * - `GET /api/schematics` — search/list schematics
 * - `GET /api/schematics/{slug}` — schematic detail
 * - `POST /api/mod/download` — download NBT file (XOR-encoded response)
 * - `POST /api/schematics/upload` — upload NBT file
 */
class CreateModSchematicSource(
    private val hmacSecret: String
) : ExternalSchematicSource {

    override val name: String = "createmod.com"
    override val baseUrl: String = "https://createmod.com"

    private val client = HttpClient.newHttpClient()
    private val gson = Gson()

    /**
     * Builds the HMAC message and signature for the current request.
     * Message format: `{timestamp}:{modVersion}:{mcUsername}:{identifier}`
     */
    private fun buildHmacHeaders(identifier: String = "greenhousesqkk"): Pair<String, String> {
        val timestamp = System.currentTimeMillis() / 1000
        val modVersion = CreateBuzzyBeez.MOD_VERSION
        val username = net.minecraft.client.Minecraft.getInstance().user.name
        val message = "$timestamp:$modVersion:$username:$identifier"
        val signature = hmacSha256(hmacSecret, message)
        return message to signature
    }

    override fun listSchematics(query: String, page: Int, pageSize: Int): CompletableFuture<SchematicListResult> {
        return CompletableFuture.supplyAsync {
            try {
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
                val apiPage = page + 1 // API is 1-indexed
                val url = "$baseUrl/api/schematics?query=$encodedQuery&page=$apiPage"

                val (message, signature) = buildHmacHeaders()

                CreateBuzzyBeez.debug { "Sending request to $url with HMAC headers: $message - $signature" }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Mod-Message", message)
                    .header("X-Mod-Signature", signature)
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    throw RuntimeException("Search failed: HTTP ${response.statusCode()}")
                }

                val json = gson.fromJson(response.body(), JsonObject::class.java)

                val items = json.getAsJsonArray("items")?.map { elem ->
                    val obj = elem.asJsonObject
                    val recordId = obj.string("id") ?: ""
                    RemoteSchematic(
                        id = obj.string("name") ?: "",
                        name = (obj.string("title") ?: "").sanitize(),
                        author = (obj.field("author")?.let { author ->
                            if (author.isJsonObject) author.asJsonObject.string("username") else author.asString
                        } ?: "Unknown").sanitize(),
                        description = (obj.string("excerpt") ?: "").sanitize(),
                        downloads = obj.int("downloads") ?: 0,
                        sizeX = obj.int("dimX") ?: 0,
                        sizeY = obj.int("dimY") ?: 0,
                        sizeZ = obj.int("dimZ") ?: 0,
                        thumbnailUrl = obj.string("featuredImage")?.let { img ->
                            if (img.isNotEmpty()) "$baseUrl/api/files/schematics/$recordId/$img?thumb=640x360" else null
                        },
                        views = obj.int("views") ?: 0,
                        blockCount = obj.int("blockCount") ?: 0,
                        rating = obj.string("rating") ?: "",
                        ratingCount = obj.int("ratingCount") ?: 0,
                        createmodVersion = (obj.string("createmodVersion") ?: "").sanitize(),
                        minecraftVersion = (obj.string("minecraftVersion") ?: "").sanitize(),
                        categories = obj.field("categories")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                            it.takeIf { it.isJsonObject }?.asJsonObject?.string("name")?.sanitize()
                        } ?: emptyList(),
                        createdHumanReadable = (obj.string("createdHumanReadable") ?: "").sanitize()
                    )
                } ?: emptyList()

                val totalPages = json.int("total") ?: 1

                SchematicListResult(
                    schematics = items,
                    totalPages = totalPages,
                    page = page,
                    pageSize = pageSize
                )
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to list schematics from $baseUrl", e)
                throw e
            }
        }
    }

    override fun downloadBytes(schematic: RemoteSchematic): CompletableFuture<ByteArray> {
        return CompletableFuture.supplyAsync {
            try {
                val timestamp = System.currentTimeMillis() / 1000
                val modVersion = CreateBuzzyBeez.MOD_VERSION
                val username = net.minecraft.client.Minecraft.getInstance().user.name
                val slug = schematic.id

                val message = "$timestamp:$modVersion:$username:$slug"
                val signature = hmacSha256(hmacSecret, message)

                val body = JsonObject().apply {
                    addProperty("message", message)
                    addProperty("signature", signature)
                    addProperty("type", "schematic")
                }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/mod/download"))
                    .header("Content-Type", "application/json")
                    // Cloudflare only lets mod requests through when they carry the signature headers
                    .header("X-Mod-Message", message)
                    .header("X-Mod-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

                if (response.statusCode() != 200) {
                    val errorBody = String(response.body(), StandardCharsets.UTF_8)
                    val errorMsg = try {
                        val errorJson = gson.fromJson(errorBody, JsonObject::class.java)
                        if (errorJson.has("error")) errorJson.get("error").asString else errorBody
                    } catch (_: Exception) {
                        "HTTP ${response.statusCode()}"
                    }
                    throw RuntimeException(errorMsg)
                }

                // XOR-decode the response bytes
                val encoded = response.body()
                val xorKey = deriveXorKey(hmacSecret, timestamp)
                xorDecode(encoded, xorKey)
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to download schematic '${schematic.name}' from $baseUrl", e)
                throw e
            }
        }
    }

    override fun download(schematic: RemoteSchematic): CompletableFuture<String> {
        return downloadBytes(schematic).thenApply { nbtBytes ->
            val slug = schematic.id
            val schematicsDir = schematicsDirectory()
            schematicsDir.mkdirs()

            var filename = "$slug.nbt"
            var targetFile = File(schematicsDir, filename)
            var counter = 1
            while (targetFile.exists()) {
                filename = "${slug}_$counter.nbt"
                targetFile = File(schematicsDir, filename)
                counter++
            }

            targetFile.writeBytes(nbtBytes)
            CreateClient.SCHEMATIC_SENDER.refresh()
            filename
        }
    }

    override fun upload(localFilename: String): CompletableFuture<RemoteSchematic> {
        return CompletableFuture.supplyAsync {
            try {
                val schematicsDir = schematicsDirectory()
                val file = File(schematicsDir, localFilename)
                if (!file.exists()) throw IllegalArgumentException("File not found: $localFilename")

                val (message, signature) = buildHmacHeaders()

                val boundary = "----CBBees${System.currentTimeMillis()}"
                val fileBytes = Files.readAllBytes(file.toPath())
                val bodyBytes = buildMultipartBody(boundary, localFilename, fileBytes)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/schematics/upload"))
                    .header("X-Mod-Message", message)
                    .header("X-Mod-Signature", signature)
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    throw RuntimeException("Upload failed: HTTP ${response.statusCode()}")
                }

                val json = gson.fromJson(response.body(), JsonObject::class.java)

                val dims = json.field("dimensions")?.takeIf { it.isJsonObject }?.asJsonObject
                RemoteSchematic(
                    id = json.string("token") ?: "",
                    name = localFilename.removeSuffix(".nbt"),
                    author = net.minecraft.client.Minecraft.getInstance().user.name,
                    description = "Uploaded from Create: Buzzy Beez",
                    downloads = 0,
                    sizeX = dims?.int("x") ?: 0,
                    sizeY = dims?.int("y") ?: 0,
                    sizeZ = dims?.int("z") ?: 0
                )
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to upload schematic '$localFilename' to $baseUrl", e)
                throw e
            }
        }
    }

    private fun buildMultipartBody(boundary: String, filename: String, fileBytes: ByteArray): ByteArray {
        val header = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        val footer = "\r\n--$boundary--\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)
        val footerBytes = footer.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(headerBytes.size + fileBytes.size + footerBytes.size)
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.size)
        System.arraycopy(fileBytes, 0, result, headerBytes.size, fileBytes.size)
        System.arraycopy(footerBytes, 0, result, headerBytes.size + fileBytes.size, footerBytes.size)
        return result
    }

    private fun schematicsDirectory(): File {
        val gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory
        return File(gameDir, "schematics")
    }

    private fun hmacSha256(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun deriveXorKey(secret: String, timestamp: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(secret.toByteArray(StandardCharsets.UTF_8))
        md.update(timestamp.toString().toByteArray(StandardCharsets.UTF_8))
        return md.digest()
    }

    private fun xorDecode(data: ByteArray, key: ByteArray): ByteArray {
        return ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
    }

    /** Strip control characters (CR, etc.) that Minecraft renders as blocks. */
    private fun String.sanitize(): String = replace(Regex("[\\x00-\\x1F\\x7F]"), "").trim()
}

/** A field of this object, or null when it is missing or JSON `null`. */
private fun JsonObject.field(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }

private fun JsonObject.string(key: String): String? = field(key)?.asString

private fun JsonObject.int(key: String): Int? = field(key)?.asInt
