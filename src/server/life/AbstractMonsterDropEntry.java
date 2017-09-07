package server.life;

import server.Randomizer;

public abstract class AbstractMonsterDropEntry
{
    public short questId;

    public int itemId, chance, minimum, maximum;

    public int getQuantity()
    {
        // 錯誤數值，最大值和最小值都不可小於一
        if (this.maximum < 1  || this.minimum < 1) {
            return 1;
        }

        // 如果相同則直接回傳
        if (this.maximum == this.minimum) {
            return this.maximum;
        }

        // 從區間中隨機選出一個數
        return Randomizer.nextInt(Math.abs(this.maximum - this.minimum) + 1) + this.minimum;
    }
}
