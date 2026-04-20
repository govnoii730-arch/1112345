package ru.mytheria.api.events.impl;


import net.minecraft.entity.Entity;
import ru.mytheria.api.events.Event;

public class AuraEvents extends Event {

    public static class AttackEvent extends AuraEvents {
        public final Entity entity;

        public AttackEvent(Entity entity) {
            this.entity = entity;
        }
    }

}
