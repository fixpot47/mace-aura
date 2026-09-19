/*
 * Mace Aura
 * Modified for a standalone Fabric mod by fixpot47 in 2026.
 *
 * Core MaceAura behavior is adapted from Aoba Client by Cocolots/coltonk9043.
 * Original source: https://github.com/Cocolots/Aoba-Client
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.fixpot47.maceaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class MaceAuraClient implements ClientModInitializer {
    public static final String MOD_ID = "maceaura";

    private static final float RADIUS = 5.0F;
    private static final float HEIGHT = 100.0F;

    private static final boolean TARGET_ANIMALS = false;
    private static final boolean TARGET_MONSTERS = true;
    private static final boolean TARGET_PLAYERS = true;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private static KeyMapping toggleKey;
    private static boolean enabled;
    private static boolean jumped;
    private static LivingEntity selectedTarget;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.maceaura.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F10,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                jumped = false;
                selectedTarget = null;

                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("Mace Aura: " + (enabled ? "ON" : "OFF"))
                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                            true
                    );
                }
            }

            if (!enabled) {
                return;
            }

            tick(client);
        });
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            selectedTarget = null;
            jumped = false;
            return;
        }

        selectTarget(client);

        if (client.player.getAttackStrengthScale(0.0F) != 1.0F) {
            return;
        }

        if (!jumped) {
            LivingEntity target = selectedTarget;
            if (!isValidTarget(target)) {
                return;
            }

            sendPaddingPackets(client);

            Vec3 newPos = client.player.position().add(0.0, HEIGHT, 0.0);
            client.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    newPos.x, newPos.y, newPos.z, false, false
            ));

            jumped = true;
            return;
        }

        sendPaddingPackets(client);

        Vec3 newPos = client.player.position();
        client.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                newPos.x, newPos.y, newPos.z, false, false
        ));

        LivingEntity target = selectedTarget;
        if (isValidTarget(target)) {
            client.gameMode.attack(client.player, target);
            client.player.swing(InteractionHand.MAIN_HAND);
        }

        jumped = false;
    }

    private static void selectTarget(Minecraft client) {
        LivingEntity best = null;
        double bestDistSqr = 0.0;
        double radiusSqr = RADIUS * RADIUS;

        for (LivingEntity entity : client.level.getEntitiesOfClass(
                LivingEntity.class,
                client.player.getBoundingBox().inflate(RADIUS),
                entity -> entity != client.player && entity.isAlive() && !entity.isRemoved()
        )) {
            boolean allowed =
                    (TARGET_PLAYERS && entity instanceof Player)
                    || (TARGET_MONSTERS && entity instanceof Enemy)
                    || (TARGET_ANIMALS && entity instanceof Animal);

            if (!allowed) {
                continue;
            }

            double distSqr = client.player.distanceToSqr(entity);
            if (distSqr > radiusSqr) {
                continue;
            }

            if (best == null || distSqr < bestDistSqr) {
                best = entity;
                bestDistSqr = distSqr;
            }
        }

        selectedTarget = best;
    }

    private static boolean isValidTarget(LivingEntity target) {
        return target != null && !target.isRemoved() && target.isAlive();
    }

    private static void sendPaddingPackets(Minecraft client) {
        int packetsRequired = Math.round((float) Math.ceil(Math.abs(HEIGHT / 10.0F)));

        for (int i = 0; i < packetsRequired; i++) {
            client.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false, false));
        }
    }
}
