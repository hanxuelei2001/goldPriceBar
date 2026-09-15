namespace GoldPriceBar.Core;

public enum PriceTrendBasis
{
    Rise,
    Fall,
    Neutral,
}

public static class PriceTrendResolver
{
    /// <summary>
    /// 设定成本价时以成本价为红绿基准（高于成本 = Rise/红，低于成本 = Fall/绿，等于成本 = 中性），
    /// 未设定成本价时沿用当日涨跌方向。
    /// </summary>
    public static PriceTrendBasis Resolve(double price, double? costPrice, bool? quoteIsNegative)
    {
        if (double.IsFinite(price) &&
            price > 0 &&
            costPrice is double cost &&
            double.IsFinite(cost) &&
            cost > 0)
        {
            if (price > cost) return PriceTrendBasis.Rise;
            if (price < cost) return PriceTrendBasis.Fall;
            return PriceTrendBasis.Neutral;
        }

        return quoteIsNegative switch
        {
            true => PriceTrendBasis.Fall,
            false => PriceTrendBasis.Rise,
            null => PriceTrendBasis.Neutral,
        };
    }
}
