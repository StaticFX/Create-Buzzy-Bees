package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Translucent ghost geometry kept on the GPU.
 *
 * Drawing ghosts through a buffer source re-emits every vertex and sorts every quad by distance
 * each frame. A mesh is uploaded once instead, and its quads are only re-sorted back to front
 * after the camera has moved [RESORT_DISTANCE] blocks relative to it.
 *
 * Must be created, drawn and closed on the render thread.
 */
@ClientSide
class GhostMesh private constructor(
    private val vertexBuffer: VertexBuffer,
    private val sortState: MeshData.SortState,
    private var sortedFrom: Vector3f,
) : AutoCloseable {

    private fun resortFor(camera: Vector3f) {
        if (camera.distanceSquared(sortedFrom) <= RESORT_DISTANCE * RESORT_DISTANCE) return
        sortState.buildSortedIndexBuffer(sortBytes, VertexSorting.byDistance(camera))?.let { indices ->
            vertexBuffer.bind()
            vertexBuffer.uploadIndexBuffer(indices)
        }
        sortedFrom = camera
    }

    override fun close() = vertexBuffer.close()

    companion object {
        private const val RESORT_DISTANCE = 1f

        /** Shared scratch space for re-sorting; each sorted index buffer is uploaded right away. */
        private val sortBytes by lazy { ByteBufferBuilder(1 shl 16) }

        /**
         * Uploads [buffers] with their alpha set to [alpha], sorted for a camera at [camera] (mesh space).
         * Returns null when there is nothing to draw.
         */
        fun build(buffers: Collection<SuperByteBuffer>, alpha: Float, camera: Vector3f): GhostMesh? {
            ByteBufferBuilder(DefaultVertexFormat.BLOCK.vertexSize * 4 * 256).use { bytes ->
                val builder = BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)
                val consumer = ColoringVertexConsumer(builder, 1f, 1f, 1f, alpha)
                val identity = PoseStack()
                buffers.forEach { it.renderInto(identity, consumer) }
                val mesh = builder.build() ?: return null
                val sortState = mesh.sortQuads(bytes, VertexSorting.byDistance(camera)) ?: run {
                    mesh.close()
                    return null
                }
                val vertexBuffer = VertexBuffer(VertexBuffer.Usage.STATIC)
                vertexBuffer.bind()
                vertexBuffer.upload(mesh)
                VertexBuffer.unbind()
                return GhostMesh(vertexBuffer, sortState, camera)
            }
        }

        /**
         * Draws [meshes] in the given order (back to front) with [pose] as their model transform
         * (mesh space to camera-relative space), setting up the translucent render state once.
         */
        fun drawAll(meshes: List<GhostMesh>, pose: Matrix4f) {
            if (meshes.isEmpty()) return
            val camera = cameraIn(pose)
            val type = RenderType.translucent()
            type.setupRenderState()
            val shader = RenderSystem.getShader()
            if (shader != null) {
                val modelView = Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose)
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, RenderSystem.getProjectionMatrix(), Minecraft.getInstance().window)
                shader.CHUNK_OFFSET?.set(0f, 0f, 0f)
                shader.apply()
                for (mesh in meshes) {
                    mesh.resortFor(camera)
                    mesh.vertexBuffer.bind()
                    mesh.vertexBuffer.draw()
                }
                VertexBuffer.unbind()
                shader.clear()
            }
            type.clearRenderState()
        }

        /** Where the camera is in the space [pose] maps from. */
        fun cameraIn(pose: Matrix4f): Vector3f = Matrix4f(pose).invert().transformPosition(Vector3f())
    }
}
