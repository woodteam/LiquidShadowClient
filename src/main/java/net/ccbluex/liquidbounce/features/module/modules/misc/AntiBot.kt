/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.NormalType
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.extensions.getFullName
import net.ccbluex.liquidbounce.utils.render.ColorUtils.stripColor
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.server.S0BPacketAnimation
import net.minecraft.network.play.server.S0CPacketSpawnPlayer
import net.minecraft.network.play.server.S13PacketDestroyEntities
import net.minecraft.network.play.server.S14PacketEntity

@ModuleInfo(name = "AntiBot", description = "Prevents KillAura from attacking AntiCheat bots.", category = ModuleCategory.MISC)
object AntiBot : Module() {

    private val tabValue = BoolValue("Tab", true)
    private val tabModeValue = ListValue("TabMode", arrayOf("Equals", "Contains"), "Contains")
    private val entityIDValue = BoolValue("EntityID", true)
    private val colorValue = BoolValue("Color", false)
    private val livingTimeValue = BoolValue("LivingTime", false)
    private val livingTimeTicksValue = IntegerValue("LivingTimeTicks", 40, 1, 200)
    private val groundValue = BoolValue("Ground", true)
    private val airValue = BoolValue("Air", false)
    private val invalidGroundValue = BoolValue("InvalidGround", true)
    private val swingValue = BoolValue("Swing", false)
    private val healthValue = BoolValue("Health", false)
    private val derpValue = BoolValue("Derp", true)
    private val wasInvisibleValue = BoolValue("WasInvisible", false)
    private val armorValue = BoolValue("Armor", false)
    private val pingValue = BoolValue("Ping", false)
    private val needHitValue = BoolValue("NeedHit", false)
    private val duplicateInWorldValue = BoolValue("DuplicateInWorld", false)
    private val duplicateInTabValue = BoolValue("DuplicateInTab", false)
    private val allwaysInRadiusValue = BoolValue("AlwaysInRadius", false)
    private val allwaysRadiusValue = FloatValue("AlwaysInRadiusBlocks", 20f, 5f, 30f)
    private val spawnWhenCombatValue = BoolValue("SpawnWhenCombat",true)

    private val removeBotsValue = BoolValue("RemoveBots",true)
    private val removeNotificationValue = BoolValue("RemoveNotification",true)

    private val ground = mutableListOf<Int>()
    private val air = mutableListOf<Int>()
    private val invalidGround = mutableMapOf<Int, Int>()
    private val swing = mutableListOf<Int>()
    private val invisible = mutableListOf<Int>()
    private val hitted = mutableListOf<Int>()
    private val notAlwaysInRadius = mutableListOf<Int>()
    private val spawnWhenCombat = mutableListOf<Int>()

    private var removedEntities = mutableListOf<Int>()

    private var inCombat = false
    private var lastAttackTime = MSTimer()

    @JvmStatic // TODO: Remove as soon EntityUtils is translated to kotlin
    fun isBot(entity: EntityLivingBase): Boolean {
        // Check if entity is a player
        if (entity !is EntityPlayer)
            return false

        // Check if anti bot is enabled
        if (!state)
            return false

        // Anti Bot checks

        if (colorValue.get() && !entity.displayName!!.formattedText.replace("§r", "").contains("§"))
            return true

        if (livingTimeValue.get() && entity.ticksExisted < livingTimeTicksValue.get())
            return true

        if (groundValue.get() && !ground.contains(entity.entityId))
            return true

        if (airValue.get() && !air.contains(entity.entityId))
            return true

        if (swingValue.get() && !swing.contains(entity.entityId))
            return true

        if (healthValue.get() && entity.health > 20F)
            return true

        if (entityIDValue.get() && (entity.entityId >= 1000000000 || entity.entityId <= -1))
            return true

        if (derpValue.get() && (entity.rotationPitch > 90F || entity.rotationPitch < -90F))
            return true

        if (wasInvisibleValue.get() && invisible.contains(entity.entityId))
            return true

        if (armorValue.get()) {
            if (entity.inventory.armorInventory[0] == null && entity.inventory.armorInventory[1] == null &&
                    entity.inventory.armorInventory[2] == null && entity.inventory.armorInventory[3] == null)
                return true
        }

        if (pingValue.get()) {
            if (mc.netHandler.getPlayerInfo(entity.uniqueID)?.responseTime == 0)
                return true
        }

        if (needHitValue.get() && !hitted.contains(entity.entityId))
            return true

        if (invalidGroundValue.get() && invalidGround.getOrDefault(entity.entityId, 0) >= 10)
            return true

        if (tabValue.get()) {
            val equals = tabModeValue.get().equals("Equals", ignoreCase = true)
            val targetName = stripColor(entity.displayName!!.formattedText)

            if (targetName != null) {
                for (networkPlayerInfo in mc.netHandler.playerInfoMap) {
                    val networkName = stripColor(networkPlayerInfo.getFullName()) ?: continue

                    if (if (equals) targetName == networkName else targetName.contains(networkName))
                        return false
                }

                return true
            }
        }

        if (duplicateInWorldValue.get() &&
                mc.theWorld!!.loadedEntityList.filter { it is EntityPlayer && it.displayNameString == it.displayNameString }.count() > 1) // TODO: I'm 99% certain this doesn't make sense
            return true

        if (duplicateInTabValue.get() &&
                mc.netHandler.playerInfoMap.filter { entity.name == stripColor(it.getFullName()) }.count() > 1)
            return true

        if (allwaysInRadiusValue.get() && !notAlwaysInRadius.contains(entity.entityId))
            return true

        if (spawnWhenCombatValue.get() && spawnWhenCombat.contains(entity.entityId))
            return true

        return entity.name!!.isEmpty() || entity.name == mc.thePlayer!!.name
    }

    override fun onDisable() {
        clearAll()
        super.onDisable()
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (mc.thePlayer == null || mc.theWorld == null)
            return

        val packet = event.packet

        if (packet is S14PacketEntity) {
            val entity = packet.getEntity(mc.theWorld!!)

            if (entity is EntityPlayer && entity != null) {
                if (entity.onGround && !ground.contains(entity.entityId))
                    ground.add(entity.entityId)

                if (!entity.onGround && !air.contains(entity.entityId))
                    air.add(entity.entityId)

                if (entity.onGround) {
                    if (entity.prevPosY != entity.posY)
                        invalidGround[entity.entityId] = invalidGround.getOrDefault(entity.entityId, 0) + 1
                } else {
                    val currentVL = invalidGround.getOrDefault(entity.entityId, 0) / 2
                    if (currentVL <= 0)
                        invalidGround.remove(entity.entityId)
                    else
                        invalidGround[entity.entityId] = currentVL
                }

                if (entity.isInvisible && !invisible.contains(entity.entityId))
                    invisible.add(entity.entityId)

                if (!notAlwaysInRadius.contains(entity.entityId) && mc.thePlayer!!.getDistanceToEntity(entity) > allwaysRadiusValue.get())
                    notAlwaysInRadius.add(entity.entityId);
            }
        }

        if (packet is S0BPacketAnimation) {
            val entity = mc.theWorld!!.getEntityByID(packet.entityID)

            if (entity != null && entity is EntityLivingBase && packet.animationType == 0
                    && !swing.contains(entity.entityId))
                swing.add(entity.entityId)
        }

        if (packet is S0CPacketSpawnPlayer) {
            if (inCombat && !removedEntities.contains(packet.entityID)) {
                spawnWhenCombat.add(packet.entityID)
            }
        }

        if (packet is S13PacketDestroyEntities) {
            removedEntities.addAll(packet.entityIDs.toTypedArray())
        }
    }

    @EventTarget
    fun onAttack(e: AttackEvent) {
        val entity = e.targetEntity

        if (entity != null && entity is EntityLivingBase && !hitted.contains(entity.entityId))
            hitted.add(entity.entityId)

        lastAttackTime.reset()
    }

    @EventTarget
    fun onWorld(event: WorldEvent?) {
        clearAll()
    }

    @EventTarget
    fun onUpdate(event:UpdateEvent) {
        if (removeBotsValue.get()) {
            for (entity in mc.theWorld.loadedEntityList) {
                if (entity is EntityLivingBase && isBot(entity) && entity != mc.thePlayer) {
                    mc.theWorld.removeEntityFromWorld(entity.entityId)
                    if (removeNotificationValue.get()) {
                        LiquidBounce.hud.addNotification(Notification(entity.name + "§r is been removed cause its a bot!",NormalType()))
                    }
                }
            }
        }
        inCombat = !lastAttackTime.hasTimePassed(1000)

    }

    private fun clearAll() {
        hitted.clear()
        swing.clear()
        ground.clear()
        invalidGround.clear()
        invisible.clear()
        notAlwaysInRadius.clear();
        spawnWhenCombat.clear()
    }

}