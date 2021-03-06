package org.terasology.additionalitempipes.action;

import org.terasology.additionalitempipes.components.SorterComponent;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.itempipes.controllers.PipeSystem;
import org.terasology.itempipes.event.PipeInsertEvent;
import org.terasology.logic.common.lifespan.LifespanComponent;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.PickupComponent;
import org.terasology.logic.inventory.events.InventorySlotChangedEvent;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.physics.components.RigidBodyComponent;
import org.terasology.registry.In;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.items.BlockItemComponent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * System covering Sorter's behavior.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class SorterAction extends BaseComponentSystem {

    @In
    PipeSystem pipeSystem;

    /**
     * Called when an item is input to the Sorter by pipe.
     * @param event PipeInsertEvent called by {@link org.terasology.itempipes.controllers.BlockMotionSystem}.
     * @param entity EntityRef to the Sorter.
     * @param sorter Sorter's SorterComponent.
     * @param block Sorter's BlockComponent.
     */
    @ReceiveEvent(components = SorterComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void onItemInput(PipeInsertEvent event, EntityRef entity, SorterComponent sorter, BlockComponent block) {
        EntityRef item = event.getActor();

        item.removeComponent(RigidBodyComponent.class);
        item.removeComponent(LifespanComponent.class);
        item.removeComponent(PickupComponent.class);

        Vector3i sorterPos = block.getPosition();

        //look for the item in the filter - if found, send the item to side according to the filter, if not - to default side set by a checkbox.
        int sideNum = 0;
        for (List<String> list : sorter.filter) {
            for (String compareString : list) {
                if (compareString.equalsIgnoreCase(getCompareString(event.getActor()))) {
                    inputOrDrop(event.getActor(), sorterPos, Side.values()[sideNum]);
                    event.consume();
                    return;
                }
            }
            sideNum++;
        }

        inputOrDrop(event.getActor(), sorterPos, Side.values()[sorter.defaultSideNum]);
        //consume the event to prevent inserting items into Sorter's inventory (used as a base for the filter)
        event.consume();
    }

    /**
     * Handles the item which came to the Sorter. Inserts the item into the according pipe, if available, otherwise - drops it.
     * @param item the item which came to the Sorter.
     * @param sorterPos position of the Sorter.
     * @param side side to which a pipe is connected.
     */
    private void inputOrDrop(EntityRef item, Vector3i sorterPos, Side side) {
        Map<Side, EntityRef> pipes = pipeSystem.findPipes(sorterPos);
        EntityRef pipe = pipes.get(side);
        if (pipe != null) {
            Set<Prefab> prefabs = pipeSystem.findingMatchingPathPrefab(pipe, side.reverse());
            Optional<Prefab> pick = prefabs.stream().skip((int) (prefabs.size() * Math.random())).findFirst();
            if (pipeSystem.insertIntoPipe(item, pipe, side.reverse(), pick.get(), 1f)) {
                return;
            }
        }
        pipeSystem.dropItem(item);
    }

    /**
     * Called when an item is added/removed into the Sorter's inventory - filter base.
     * @param event Event triggered when items are added/removed
     * @param entity EntityRef to the Sorter.
     * @param sortComp SorterComponent of the Sorter.
     * @param inv InventoryComponent of the Sorter.
     */
    @ReceiveEvent(components = {SorterComponent.class, InventoryComponent.class})
    public void onFilterChange(InventorySlotChangedEvent event, EntityRef entity, SorterComponent sortComp, InventoryComponent inv) {
        List<List<String>> newFilter = new LinkedList<>();
        newFilter.add(new ArrayList<>());
        newFilter.add(new ArrayList<>());
        newFilter.add(new ArrayList<>());
        newFilter.add(new ArrayList<>());
        newFilter.add(new ArrayList<>());
        newFilter.add(new ArrayList<>());

        int slotNum = 0;
        for (EntityRef slot : inv.itemSlots) {
            if (slot != EntityRef.NULL) {
                int sideNumber = slotNum / 5;
                newFilter.get(sideNumber).add(getCompareString(slot));
            }
            slotNum++;
        }

        sortComp.filter = newFilter;
        entity.saveComponent(sortComp);
    }

    /**
     * Gets a string off the entity used for filtering. The items are differentiated by their's prefab name, block - by their block family's name.
     * @param item Item used to generate the string for filtering purposes.
     * @return String generated for filtering purposes.
     */
    private String getCompareString(EntityRef item) {
        BlockItemComponent biComponent = item.getComponent(BlockItemComponent.class);
        if (biComponent != null) {
            return biComponent.blockFamily.getURI().toString();
        } else {
            return item.getParentPrefab().getName();
        }
    }
}
