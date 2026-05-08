package net.turtton.connectedtank.block

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
//? if fabric {
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage
//?} else if neoforge {
/*import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.transfer.fluid.FluidResource
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler*/
//?}
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.fluid.PlatformFluidVariant

//? if fabric {
class TankFluidStorage(val bucketCapacity: Int = CTServerConfig.instance.tankBucketCapacity, fluid: ExistingData? = null) : SingleVariantStorage<FluidVariant>() {
    constructor(bucketCapacity: Int, fluid: Optional<ExistingData>) : this(bucketCapacity, fluid.getOrNull())

    var onChanged: (() -> Unit)? = null

    init {
        if (fluid != null) {
            val (variant, amount) = fluid
            this.variant = variant
            this.amount = amount
        }
    }

    override fun getBlankVariant(): FluidVariant = FluidVariant.blank()

    override fun getCapacity(variant: FluidVariant): Long = bucketCapacity * FluidConstants.BUCKET

    override fun onFinalCommit() {
        onChanged?.invoke()
    }

    data class ExistingData(val variant: PlatformFluidVariant, val amount: Long) {
        companion object {
            val CODEC: Codec<ExistingData> = RecordCodecBuilder.create {
                it.group(
                    FluidVariant.CODEC.fieldOf("variant").forGetter(ExistingData::variant),
                    Codec.LONG.fieldOf("amount").forGetter(ExistingData::amount),
                ).apply(it, ::ExistingData)
            }

            fun optional(storage: TankFluidStorage): Optional<ExistingData> = if (storage.variant.isBlank) Optional.empty() else Optional.of(ExistingData(storage.variant, storage.amount))
        }
    }

    companion object {
        val CODEC: Codec<TankFluidStorage> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("size").forGetter(TankFluidStorage::bucketCapacity),
                ExistingData.CODEC.optionalFieldOf("fluid").forGetter(ExistingData::optional),
            ).apply(instance, ::TankFluidStorage)
        }
    }
}
//?} else if neoforge {
/*class TankFluidStorage(val bucketCapacity: Int = CTServerConfig.instance.tankBucketCapacity, fluid: ExistingData? = null) : FluidStacksResourceHandler(1, bucketCapacity * FluidType.BUCKET_VOLUME) {
    constructor(bucketCapacity: Int, fluid: Optional<ExistingData>) : this(bucketCapacity, fluid.getOrNull())

    var onChanged: (() -> Unit)? = null

    val fluidStack: FluidStack get() = stacks[0]
    val variant: PlatformFluidVariant get() = fluidStack
    val amount: Long get() = fluidStack.amount.toLong()
    val isResourceBlank: Boolean get() = fluidStack.isEmpty

    init {
        if (fluid != null) {
            stacks[0] = fluid.variant.copyWithAmount(fluid.amount.toInt())
        }
    }

    override fun onContentsChanged(index: Int, previousContents: FluidStack) {
        onChanged?.invoke()
    }

    data class ExistingData(val variant: PlatformFluidVariant, val amount: Long) {
        companion object {
            val CODEC: Codec<ExistingData> = RecordCodecBuilder.create {
                it.group(
                    FluidStack.CODEC.fieldOf("variant").forGetter(ExistingData::variant),
                    Codec.LONG.fieldOf("amount").forGetter(ExistingData::amount),
                ).apply(it, ::ExistingData)
            }

            fun optional(storage: TankFluidStorage): Optional<ExistingData> =
                if (storage.isResourceBlank) Optional.empty()
                else Optional.of(ExistingData(storage.variant, storage.amount))
        }
    }

    companion object {
        val CODEC: Codec<TankFluidStorage> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("size").forGetter(TankFluidStorage::bucketCapacity),
                ExistingData.CODEC.optionalFieldOf("fluid").forGetter(ExistingData::optional),
            ).apply(instance, ::TankFluidStorage)
        }
    }
}*/
//?}
