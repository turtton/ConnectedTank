package net.turtton.connectedtank.block

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage

class TankFluidStorage(val bucketCapacity: Int = 32, fluid: ExistingData? = null) : SingleVariantStorage<FluidVariant>() {
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

    override fun getCapacity(variant: FluidVariant?): Long = bucketCapacity * FluidConstants.BUCKET

    override fun onFinalCommit() {
        onChanged?.invoke()
    }

    data class ExistingData(val variant: FluidVariant, val amount: Long) {
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
