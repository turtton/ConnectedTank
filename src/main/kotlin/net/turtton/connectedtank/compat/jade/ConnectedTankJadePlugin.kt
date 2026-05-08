package net.turtton.connectedtank.compat.jade

//? if fabric {
//?}
import net.minecraft.server.level.ServerLevel
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.turtton.connectedtank.MOD_ID
import net.turtton.connectedtank.block.ConnectedTankBlockEntity
import net.turtton.connectedtank.fluid.FLUID_BUCKET
import net.turtton.connectedtank.world.FluidStoragePersistentState
import snownee.jade.api.Accessor
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.fluid.JadeFluidObject
import snownee.jade.api.view.ClientViewGroup
import snownee.jade.api.view.FluidView
import snownee.jade.api.view.IClientExtensionProvider
import snownee.jade.api.view.IServerExtensionProvider
import snownee.jade.api.view.ViewGroup

@WailaPlugin
class ConnectedTankJadePlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerFluidStorage(TankFluidProvider, ConnectedTankBlockEntity::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerFluidStorageClient(TankFluidProvider)
    }
}

object TankFluidProvider :
    IServerExtensionProvider<FluidView.Data>,
    IClientExtensionProvider<FluidView.Data, FluidView> {
    override fun getUid(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "tank_fluid")

    override fun getGroups(accessor: Accessor<*>): List<ViewGroup<FluidView.Data>>? {
        val blockAccessor = accessor as BlockAccessor
        val world = blockAccessor.level as ServerLevel
        val pos = blockAccessor.position

        val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
        val storage = persistentState.getStorage(pos) ?: return null
        if (storage.isResourceBlank || storage.amount <= 0) return null

        val variant = storage.variant
        //? if fabric {
        //? if >=26.1 {
        /*val fluidObject = JadeFluidObject.of(variant.fluid, storage.amount, variant.componentsPatch)*/
        //?} else {
        val fluidObject = JadeFluidObject.of(variant.fluid, storage.amount, variant.components)
        //?}
        //?} else if neoforge {
        /*val fluidObject = JadeFluidObject.of(variant.fluid, storage.amount, variant.componentsPatch)*/
        //?}
        val capacity = storage.bucketCapacity.toLong() * FLUID_BUCKET
        val data = FluidView.Data(fluidObject, capacity)
        return listOf(ViewGroup(listOf(data)))
    }

    override fun getClientGroups(
        accessor: Accessor<*>,
        groups: List<ViewGroup<FluidView.Data>>,
    ): List<ClientViewGroup<FluidView>> = ClientViewGroup.map(groups, FluidView::readDefault, null)
}
