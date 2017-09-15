/*
 * TMS 113 server/CashItemInfo.java
 *
 * Copyright (C) 2017 ~ Present
 *
 * Patrick Huy <patrick.huy@frz.cc>
 * Matthias Butz <matze@odinms.de>
 * Jan Christian Meyer <vimes@odinms.de>
 * freedom <freedom@csie.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package server;

public class CashItemInfo
{
    public int sn, itemId, count, price, period, priority, gender;
    public int mark, meso, unk_1, unk_2, unk_3, flags;
    public boolean showUp;

    CashItemInfo(int sn, int itemId, int count, int price, int period, int priority, int gender, int mark, int showUp, int packaged, int meso, int unk_1, int unk_2, int unk_3)
    {
        this.sn = sn;
        this.itemId = itemId;
        this.count = count;
        this.price = price;
        this.period = period;
        this.priority = priority;
        this.gender = gender;
        this.mark = mark;
        this.showUp = showUp == 1;
        this.meso = meso;
        this.unk_1 = unk_1;
        this.unk_2 = unk_2;
        this.unk_3 = unk_3;

        if (this.itemId > 0) {
            this.flags |= 1;
        }

        if (this.count > 0) {
            this.flags |= 2;
        }

        if (this.price > 0) {
            this.flags |= 4;
        }

        if (this.unk_1 > 0) {
            this.flags |= 8;
        }

        if (this.priority >= 0) {
            this.flags |= 16;
        }

        if (this.period > 0) {
            this.flags |= 32;
        }

        if (this.meso > 0) {
            this.flags |= 128;
        }

        if (this.unk_2 > 0) {
            this.flags |= 256;
        }

        if (this.gender >= 0) {
            this.flags |= 512;
        }

        if (this.showUp) {
            this.flags |= 1024;
        }

        if ((this.mark >= -1) && (this.mark <= 3)) {
            this.flags |= 2048;
        }

        if (this.unk_3 > 0) {
            this.flags |= 4096;
        }

        if (packaged == 1) {
            this.flags |= 0x10000;
        }
    }

    public int getSN()
    {
        return this.sn;
    }

    public int getId()
    {
        return this.itemId;
    }

    public int getPrice()
    {
        return this.price;
    }

    public int getCount()
    {
        return this.count;
    }

    public int getPeriod()
    {
        return this.period;
    }

    public int getGender()
    {
        return this.gender;
    }

    public int getMark()
    {
        return this.mark;
    }

    public boolean getShowUp()
    {
        return this.showUp;
    }

    public int getMeso()
    {
        return this.meso;
    }

    public boolean genderEquals(int g)
    {
        return this.gender == g || this.gender == 2;
    }
}
