import Foundation
import XCTest
@testable import goldPriceBar

final class CostPriceTests: XCTestCase {
    // MARK: - 红绿基准

    func testTrendUsesCostPriceWhenSet() {
        // 高于成本价 = 红
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: 900, isNegative: true), .rise)
        // 低于成本价 = 绿
        XCTAssertEqual(GoldPriceTrend(price: 850, costPrice: 900, isNegative: false), .fall)
        // 与成本价持平 = 中性
        XCTAssertEqual(GoldPriceTrend(price: 900, costPrice: 900, isNegative: false), .neutral)
    }

    func testTrendFallsBackToQuoteDirectionWithoutUsableCostPrice() {
        // 未设置成本价
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: nil, isNegative: false), .rise)
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: nil, isNegative: true), .fall)
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: nil, isNegative: nil), .neutral)

        // 成本价非法
        for invalid in [0, -1, .nan, .infinity] {
            XCTAssertEqual(
                GoldPriceTrend(price: 950, costPrice: invalid, isNegative: true),
                .fall,
                "成本价 \(invalid) 应回退到涨跌方向"
            )
        }

        // 价格本身无效时不能与成本价比较
        XCTAssertEqual(GoldPriceTrend(price: 0, costPrice: 900, isNegative: false), .rise)
        XCTAssertEqual(GoldPriceTrend(price: .nan, costPrice: 900, isNegative: true), .fall)
    }

    func testCostBasisDrivesCharacterEmotion() {
        // 跌破成本价 → 难过；高于成本价 / 持平 → 开心
        XCTAssertEqual(GoldPriceTrend(price: 850, costPrice: 900, isNegative: false).emotion, .sad)
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: 900, isNegative: true).emotion, .happy)
        XCTAssertEqual(GoldPriceTrend(price: 900, costPrice: 900, isNegative: false).emotion, .happy)
        // 未设置成本价时与原有涨跌语义一致
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: nil, isNegative: true).emotion, .sad)
        XCTAssertEqual(GoldPriceTrend(price: 950, costPrice: nil, isNegative: nil).emotion, .happy)
    }

    func testTrendColorsMatchRedRiseGreenFall() {
        XCTAssertEqual(GoldPriceTrend.rise.color, NSColor(calibratedRed: 0.95, green: 0.25, blue: 0.22, alpha: 1))
        XCTAssertEqual(GoldPriceTrend.fall.color, NSColor(calibratedRed: 0.2, green: 0.78, blue: 0.35, alpha: 1))
        XCTAssertEqual(GoldPriceTrend.neutral.color, .secondaryLabelColor)
    }

    // MARK: - 单击轮换数据源

    func testClickCyclesProvidersInMenuOrderAndWraps() {
        XCTAssertEqual(GoldProvider.allCases, [.zheShang, .minSheng, .gongShang])
        XCTAssertEqual(GoldProvider.next(after: .zheShang), .minSheng)
        XCTAssertEqual(GoldProvider.next(after: .minSheng), .gongShang)
        XCTAssertEqual(GoldProvider.next(after: .gongShang), .zheShang)

        // 连续切换三次回到起点
        var provider = GoldProvider.zheShang
        for _ in 0..<GoldProvider.allCases.count {
            provider = GoldProvider.next(after: provider)
        }
        XCTAssertEqual(provider, .zheShang)
    }

    func testSignLabelUsesProviderShortName() {
        XCTAssertEqual(GoldProvider.zheShang.shortName, "浙商")
        XCTAssertEqual(GoldProvider.minSheng.shortName, "民生")
        XCTAssertEqual(GoldProvider.gongShang.shortName, "工银")
    }

    // MARK: - 成本价持久化

    func testCostPriceStoreRoundTripsPerProvider() throws {
        let suiteName = "CostPriceTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        XCTAssertEqual(CostPriceStore.load(from: defaults), [:])

        let prices: [GoldProvider: Double] = [.zheShang: 880.5, .gongShang: 901.25]
        CostPriceStore.save(prices, to: defaults)

        let loaded = CostPriceStore.load(from: defaults)
        XCTAssertEqual(loaded, prices)
        XCTAssertNil(loaded[.minSheng])

        // 清除后不再回读
        CostPriceStore.save([:], to: defaults)
        XCTAssertEqual(CostPriceStore.load(from: defaults), [:])
    }

    func testCostPriceStoreIgnoresInvalidStoredValues() throws {
        let suiteName = "CostPriceTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        defaults.set(0, forKey: CostPriceStore.key(for: .zheShang))
        defaults.set(-5, forKey: CostPriceStore.key(for: .minSheng))
        defaults.set(900, forKey: CostPriceStore.key(for: .gongShang))

        XCTAssertEqual(CostPriceStore.load(from: defaults), [.gongShang: 900])
    }
}
