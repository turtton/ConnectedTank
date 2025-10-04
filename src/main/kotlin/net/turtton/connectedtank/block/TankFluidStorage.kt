package net.turtton.connectedtank.block

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage
import net.turtton.connectedtank.extension.toKotlinPairCodec

class TankFluidStorage(val bucketCapacity: Int = 32, fluid: Pair<FluidVariant, Long>? = null) : SingleVariantStorage<FluidVariant>() {
    constructor(bucketCapacity: Int, fluid: Optional<Pair<FluidVariant, Long>>) : this(bucketCapacity, fluid.getOrNull())

    init {
        if (fluid != null) {
            val (variant, amount) = fluid
            this.variant = variant
            this.amount = amount
        }
    }

    override fun getBlankVariant(): FluidVariant = FluidVariant.blank()

    override fun getCapacity(variant: FluidVariant?): Long = bucketCapacity * FluidConstants.BUCKET / 81

    override fun onFinalCommit() {
        // TODO()
    }

    companion object {
        val CODEC: Codec<TankFluidStorage> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("size").forGetter(TankFluidStorage::bucketCapacity),
                Codec.pair(FluidVariant.CODEC, Codec.LONG).toKotlinPairCodec().optionalFieldOf("fluid").forGetter {
                    if (it.variant.isBlank) Optional.empty() else Optional.of(Pair(it.variant, it.amount))
                },
            ).apply(instance, ::TankFluidStorage)
        }
    }
}
