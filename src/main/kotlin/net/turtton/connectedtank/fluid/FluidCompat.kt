package net.turtton.connectedtank.fluid

//? if fabric {
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
//?} else if neoforge {
/*import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.transfer.fluid.FluidResource*/
//?}
import net.minecraft.world.level.material.Fluid
import net.turtton.connectedtank.block.TankFluidStorage

//? if fabric {
typealias PlatformFluidVariant = FluidVariant

val FLUID_BUCKET: Long = FluidConstants.BUCKET

fun blankFluidVariant(): PlatformFluidVariant = FluidVariant.blank()

fun fluidVariantOf(fluid: Fluid): PlatformFluidVariant = FluidVariant.of(fluid)

fun PlatformFluidVariant.isBlankVariant(): Boolean = isBlank

fun PlatformFluidVariant.getVariantFluid(): Fluid = fluid

fun PlatformFluidVariant.isSameFluid(other: PlatformFluidVariant): Boolean = fluid == other.fluid

fun insertFluid(storage: TankFluidStorage, variant: PlatformFluidVariant, amount: Long) {
    Transaction.openOuter().use { tx ->
        storage.insert(variant, amount, tx)
        tx.commit()
    }
}
//?} else if neoforge {
/*typealias PlatformFluidVariant = FluidStack

val FLUID_BUCKET: Long = FluidType.BUCKET_VOLUME.toLong()

fun blankFluidVariant(): PlatformFluidVariant = FluidStack.EMPTY

fun fluidVariantOf(fluid: Fluid): PlatformFluidVariant = FluidStack(fluid, 1)

fun PlatformFluidVariant.isBlankVariant(): Boolean = isEmpty

fun PlatformFluidVariant.getVariantFluid(): Fluid = fluid

fun PlatformFluidVariant.isSameFluid(other: PlatformFluidVariant): Boolean = fluid == other.fluid

fun insertFluid(storage: TankFluidStorage, variant: PlatformFluidVariant, amount: Long) {
    val current = storage.fluidStack
    val newAmount = (current.amount + amount.toInt()).coerceAtMost(storage.bucketCapacity * FluidType.BUCKET_VOLUME)
    storage.set(0, FluidResource.of(variant.fluid), newAmount)
}*/
//?}
