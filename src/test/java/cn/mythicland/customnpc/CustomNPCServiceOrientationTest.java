package cn.mythicland.customnpc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomNPCServiceOrientationTest {

    @Test
    void placementBodyUsesTheSameSnappedYawAsTheHead() {
        World world = testWorld();
        Location npc = new Location(world, 0.5D, 7.0D, 0.5D, 120.0F, 20.0F);
        Location player = new Location(world, 0.6D, 8.0D, 2.4D, 180.0F, 40.0F);

        Location placement = CustomNPCService.snapFacingPlayer(npc, player);
        Location headTarget = CustomNPCService.snappedHeadTarget(placement, player);
        Location head = placement.clone();
        Vector direction = headTarget.toVector().subtract(placement.toVector());
        direction.setY(0.0D);
        head.setDirection(direction);

        assertEquals(head.getYaw(), placement.getYaw());
        assertEquals(0.0F, placement.getPitch());
    }

    @Test
    void headTargetUsesTheSameSnappedCompassRay() {
        World world = testWorld();
        Location npc = new Location(world, 0.5D, 7.0D, 0.5D, 45.0F, 0.0F);
        Location firstPlayer = new Location(world, 1.1D, 8.0D, 2.1D, 12.0F, 41.0F);
        Location secondPlayer = new Location(world, 1.4D, 8.0D, 2.4D, 78.0F, -20.0F);

        Location firstTarget = CustomNPCService.snappedHeadTarget(npc, firstPlayer);
        Location secondTarget = CustomNPCService.snappedHeadTarget(npc, secondPlayer);
        Location view = npc.clone();
        Vector direction = firstTarget.toVector().subtract(npc.toVector());
        direction.setY(0.0D);
        view.setDirection(direction);

        assertEquals(firstTarget.getX(), secondTarget.getX());
        assertEquals(firstTarget.getZ(), secondTarget.getZ());
        assertEquals(315.0F, view.getYaw());
        assertEquals(0.0F, firstTarget.getPitch());
    }

    @Test
    void southPlacementFacesThePlayerDirectly() {
        World world = testWorld();
        Location npc = new Location(world, 0.5D, 7.0D, 0.5D, 90.0F, 0.0F);
        Location player = new Location(world, 0.6D, 8.0D, 2.4D, 0.0F, 0.0F);

        Location placement = CustomNPCService.snapFacingPlayer(npc, player);
        Location headTarget = CustomNPCService.snappedHeadTarget(placement, player);
        Location head = placement.clone();
        Vector direction = headTarget.toVector().subtract(placement.toVector());
        direction.setY(0.0D);
        head.setDirection(direction);

        assertEquals(0.0F, placement.getYaw());
        assertEquals(0.0F, head.getYaw());
    }

    @Test
    void nameDisplayKeepsBottomLineAtConfiguredOffset() {
        World world = testWorld();
        Location npc = new Location(world, 0.5D, 7.0D, 0.5D);

        Location anchor = CustomNPCService.nameDisplayAnchor(
                npc,
                2.3D,
                List.of(0.0D, 0.4D, 0.6D)
        );

        assertEquals(9.3D, anchor.getY() - 0.4D - 0.6D, 1.0E-9D);
        assertEquals(10.3D, anchor.getY(), 1.0E-9D);
        assertEquals(npc.getX(), anchor.getX());
        assertEquals(npc.getZ(), anchor.getZ());
    }

    @Test
    void selectionRayCanHitTheNpcBodyAboveItsFeet() {
        World world = testWorld();
        Location eye = new Location(world, 0.5D, 8.62D, 0.5D);
        Location npc = new Location(world, 0.5D, 7.0D, -3.5D);
        Vector direction = new Vector(0.0D, -0.42D, -4.0D).normalize();

        double hitDistance = CustomNPCService.selectionDistance(eye, direction, npc, 6.0D, 0.75D);

        assertTrue(Double.isFinite(hitDistance), "A ray through the NPC head should select its body");
    }

    @Test
    void selectionRayRejectsNpcOutsideItsHorizontalEnvelope() {
        World world = testWorld();
        Location eye = new Location(world, 0.5D, 8.62D, 0.5D);
        Location npc = new Location(world, 2.0D, 7.0D, -3.5D);
        Vector direction = new Vector(0.0D, -0.42D, -4.0D).normalize();

        double hitDistance = CustomNPCService.selectionDistance(eye, direction, npc, 6.0D, 0.75D);

        assertTrue(Double.isInfinite(hitDistance), "A ray beside the NPC should not select it");
    }

    private static World testWorld() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test-world";
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
    }
}
