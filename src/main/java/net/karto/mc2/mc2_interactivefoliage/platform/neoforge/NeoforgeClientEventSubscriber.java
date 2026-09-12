package net.karto.mc2.mc2_interactivefoliage.platform.neoforge;

//? neoforge {

/*import net.karto.mc2.mc2_interactivefoliage.ModTemplate;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(
        modid = ModTemplate.MOD_ID,
        value = Dist.CLIENT
)
public class NeoforgeClientEventSubscriber {

    @SubscribeEvent
    public static void registerKeyMappings(
            RegisterKeyMappingsEvent event
    ) {
        NeoforgeKeyBindings.register(
                event::register
        );
    }

    @SubscribeEvent
    public static void onClientTick(
            ClientTickEvent.Post event
    ) {
        NeoforgeKeyBindings.tick(
                Minecraft.getInstance()
        );
    }
}
*///?}