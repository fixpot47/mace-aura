/*
 * Mace Aura
 * Modified for a standalone Fabric mod by fixpot47 in 2026.
 *
 * Inspired by the combat automation behavior of Aoba Client.
 * Original project: https://github.com/Cocolots/Aoba-Client
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

public final class MaceAuraClient implements ClientModInitializer {
    public static final String MOD_ID = "maceaura";

    private static final double TARGET_RADIUS = 5.0D;
    private static final double ATTACK_RANGE = 4.5D;
    private static final double REQUIRED_HEIGHT = 2.0D;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private static KeyMapping toggleKey;
    private static boolean enabled;
    private static boolean wasOnGround = true;
    private static boolean jumpedFromGround;
    private static boolean attackedThisJump;
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
                selectedTarget = null;
                jumpedFromGround = false;
                attackedThisJump = false;

                client.gui.hud.setOverlayMessage(
                        Component.literal(enabled ? "Mace Aura enabled" : "Mace Aura disabled"),
                        false
                );
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
            jumpedFromGround = false;
            attackedThisJump = false;
            wasOnGround = true;
            return;
        }

        boolean onGround = client.player.onGround();

        double verticalVelocity = client.player.getDeltaMovement().y;

        if (onGround) {
            jumpedFromGround = false;
            attackedThisJump = false;
        } else if (wasOnGround && verticalVelocity > 0.0D) {
            // Normal jump from the ground.
            jumpedFromGround = true;
            attackedThisJump = false;
        } else if (attackedThisJump && verticalVelocity > 0.08D) {
            // Wind Burst launches the player upward after a successful mace smash.
            // Treat that rebound as a new airborne attack cycle so another smash
            // can happen on the next descent without touching the ground first.
            jumpedFromGround = true;
            attackedThisJump = false;
            selectedTarget = null;
        }

        wasOnGround = onGround;

        if (!jumpedFromGround || attackedThisJump) {
            selectedTarget = null;
            return;
        }

        if (client.player.getMainHandItem().getItem() != Items.MACE) {
            selectedTarget = null;
            return;
        }

        selectedTarget = findTarget(client);
        LivingEntity target = selectedTarget;

        if (!isValidTarget(target)) {
            return;
        }


        double verticalDifference = client.player.getY() - target.getY();
        if (verticalDifference < REQUIRED_HEIGHT) {
            return;
        }

        // Wait until the player is actually falling after the jump.
        if (client.player.getDeltaMovement().y >= 0.0D) {
            return;
        }

        if (client.player.getAttackStrengthScale(0.0F) < 0.99F) {
            return;
        }

        if (client.player.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE) {
            return;
        }

        if (!client.player.hasLineOfSight(target)) {
            return;
        }

        SwingAnimation swingAnimation = client.player.getMainHandItem().getAttackAnimation();
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND, swingAnimation, false);
        client.player.connection.send(ServerboundPunchPacket.INSTANCE);
        attackedThisJump = true;
    }

    private static LivingEntity findTarget(Minecraft client) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double radiusSqr = TARGET_RADIUS * TARGET_RADIUS;

        for (LivingEntity entity : client.level.getEntitiesOfClass(
                LivingEntity.class,
                client.player.getBoundingBox().inflate(TARGET_RADIUS),
                entity -> entity != client.player && entity.isAlive() && !entity.isRemoved()
        )) {
            if (!(entity instanceof Player) && !(entity instanceof Mob) && !(entity instanceof Mannequin)) {
                continue;
            }

            if (client.player.getY() - entity.getY() < REQUIRED_HEIGHT) {
                continue;
            }

            double distance = client.player.distanceToSqr(entity);
            if (distance > radiusSqr) {
                continue;
            }

            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static boolean isValidTarget(LivingEntity target) {
        return target != null && !target.isRemoved() && target.isAlive();
    }
}
