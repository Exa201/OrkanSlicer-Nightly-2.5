package ru.ytkab0bp.slicebeam.manufacturing

import ru.ytkab0bp.slicebeam.slic3r.Native

/** Read-only, index-topology audit of one model object. It does not repair or mutate the mesh. */
data class MeshTopologyAudit(
    val analysisAvailable: Boolean,
    val validIndices: Boolean,
    val connectedComponents: Int,
    val closedManifoldComponents: Int,
    val boundaryEdges: Int,
    val nonManifoldEdges: Int,
    val degenerateFaces: Int,
    val triangleCount: Int,
) {
    /** Reports indexed-edge topology only; this does not establish absence of self-intersections. */
    val hasClosedManifoldTopology: Boolean
        get() = analysisAvailable && validIndices && triangleCount > 0 && connectedComponents > 0 &&
            closedManifoldComponents == connectedComponents && boundaryEdges == 0 &&
            nonManifoldEdges == 0 && degenerateFaces == 0

    companion object {
        /** Decode the stable native result shape, rejecting malformed native responses. */
        fun fromNative(values: IntArray): MeshTopologyAudit? {
            if (values.size != 8 || values[0] !in 0..1 || values[1] !in 0..1 || values.drop(2).any { it < 0 }) return null
            return MeshTopologyAudit(
                analysisAvailable = values[0] == 1,
                validIndices = values[1] == 1,
                connectedComponents = values[2],
                closedManifoldComponents = values[3],
                boundaryEdges = values[4],
                nonManifoldEdges = values[5],
                degenerateFaces = values[6],
                triangleCount = values[7],
            )
        }

        /** Analyze the original model-part mesh; modifiers are excluded by the native model API. */
        fun analyze(modelPointer: Long, objectIndex: Int): MeshTopologyAudit? {
            if (modelPointer == 0L || objectIndex < 0) return null
            return fromNative(Native.model_audit_object_mesh_topology(modelPointer, objectIndex))
        }
    }
}
