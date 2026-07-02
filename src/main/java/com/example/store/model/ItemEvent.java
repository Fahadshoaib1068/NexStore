package com.example.store.model;

import java.io.Serializable;

public class ItemEvent implements Serializable {

    public enum Action {
        CREATED, UPDATED, DELETED
    }

    private Action action;
    private Item   item;
    private Integer itemId; // used for DELETE (no need to send full item)

    public ItemEvent() {}

    public ItemEvent(Action action, Item item) {
        this.action = action;
        this.item   = item;
        this.itemId = item != null ? item.getItem_id() : null;
    }

    public ItemEvent(Action action, Integer itemId) {
        this.action = action;
        this.itemId = itemId;
        this.item   = null;
    }

    public Action  getAction() { return action; }
    public Item    getItem()   { return item; }
    public Integer getItemId() { return itemId; }

    public void setAction(Action action)   { this.action = action; }
    public void setItem(Item item)         { this.item = item; }
    public void setItemId(Integer itemId)  { this.itemId = itemId; }

    @Override
    public String toString() {
        return "ItemEvent{action=" + action + ", itemId=" + itemId + "}";
    }
}