package org.infinite.libs.client.aim.task.config

import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import org.infinite.libs.client.aim.camera.CameraRoll

sealed class AimTarget {
    open class EntityTarget(
        e: Entity,
    ) : AimTarget() {
        open val entity = e
    }

    open class BlockTarget(
        b: BlockEntity,
    ) : AimTarget() {
        open val block = b
    }

    open class WaypointTarget(
        p: Vec3d,
    ) : AimTarget() {
        open val pos = p
    }

    open class RollTarget(
        r: CameraRoll,
    ) : AimTarget() {
        open val roll = r
    }
}
