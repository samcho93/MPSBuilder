package com.example.mpsbuilder.ui.workbench.workpiece

import com.example.mpsbuilder.ui.workbench.model.Workpiece
import com.example.mpsbuilder.ui.workbench.model.WorkpieceType
import java.util.UUID
import javax.inject.Inject

class WorkpieceSupplier @Inject constructor() {

    fun supply(
        type: WorkpieceType,
        supplierId: String,
        targetConveyorId: String?,
        currentWorkpieces: List<Workpiece>
    ): Workpiece {
        return Workpiece(
            id = UUID.randomUUID().toString(),
            type = type,
            positionX = 0f,
            positionY = 0f,
            onConveyorId = targetConveyorId,
            conveyorProgress = 0f
        )
    }
}
