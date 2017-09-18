package server;

import client.inventory.IItem;

public class MapleDueyActions
{
    private int id = 0;
    private String sender = null;
    private IItem item = null;
    private int mesos = 0;
    private int quantity = 1;
    private long sentAt;

    public MapleDueyActions(int id)
    {
        this.id = id;
    }

    public MapleDueyActions(int id, IItem item)
    {
        this.id = id;
        this.item = item;

        if (this.item != null) {
            this.quantity = item.getQuantity();
        }
    }

    public final int getId()
    {
        return this.id;
    }

    public final String getSender()
    {
        return this.sender;
    }

    public void setSender(final String name)
    {
        this.sender = name;
    }

    public final IItem getItem()
    {
        return this.item;
    }

    public final int getMesos()
    {
        return this.mesos;
    }

    public void setMesos(final int set)
    {
        this.mesos = set;
    }

    public final int getQuantity()
    {
        return this.quantity;
    }

    public void setSentAt(final long sentAt)
    {
        this.sentAt = sentAt;
    }

    public final long getSentAt()
    {
        return this.sentAt;
    }
}
