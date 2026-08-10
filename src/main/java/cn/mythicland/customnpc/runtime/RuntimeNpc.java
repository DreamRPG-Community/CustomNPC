package cn.mythicland.customnpc.runtime;

import cn.mythicland.lib.text.FloatingTextHandle;
import cn.mythicland.thirdparty.npclib.api.NPC;

import java.util.Objects;

/**
 * Runtime-only NPCLib and floating-text handles for one persisted NPC.
 */
public record RuntimeNpc(NPC npc, FloatingTextHandle nameHandle) {

    public RuntimeNpc(NPC npc, FloatingTextHandle nameHandle) {
        this.npc = Objects.requireNonNull(npc, "npc");
        this.nameHandle = Objects.requireNonNull(nameHandle, "nameHandle");
    }

    public void destroy() {
        nameHandle.close();
        if (npc.isCreated()) npc.destroy();
    }
}
