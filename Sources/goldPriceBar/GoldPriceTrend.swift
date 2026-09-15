import AppKit

/// 金价红绿基准。
///
/// 设定了成本价时以成本价为基准比较（高于成本为红、低于成本为绿），
/// 未设定成本价时回退到接口返回的当日涨跌方向。
enum GoldPriceTrend: Equatable {
    case rise
    case fall
    case neutral

    /// 沿用当日涨跌方向。
    init(isNegative: Bool?) {
        switch isNegative {
        case true: self = .fall
        case false: self = .rise
        case nil: self = .neutral
        }
    }

    /// 红绿基准：优先按成本价比较，成本价无效时回退到当日涨跌方向。
    init(price: Double, costPrice: Double?, isNegative: Bool?) {
        guard let costPrice,
              costPrice.isFinite,
              costPrice > 0,
              price.isFinite,
              price > 0
        else {
            self.init(isNegative: isNegative)
            return
        }

        if price > costPrice {
            self = .rise
        } else if price < costPrice {
            self = .fall
        } else {
            self = .neutral
        }
    }

    /// 看板娘情绪：跌破成本价（或当日下跌）时难过，其余情况开心。
    var emotion: FloatingCharacterEmotion {
        self == .fall ? .sad : .happy
    }

    var color: NSColor {
        switch self {
        case .rise:
            return NSColor(calibratedRed: 0.95, green: 0.25, blue: 0.22, alpha: 1)
        case .fall:
            return NSColor(calibratedRed: 0.2, green: 0.78, blue: 0.35, alpha: 1)
        case .neutral:
            return .secondaryLabelColor
        }
    }
}
