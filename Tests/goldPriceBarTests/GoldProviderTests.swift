import Foundation
import XCTest
@testable import goldPriceBar

final class GoldProviderTests: XCTestCase {
    func testAllProvidersAreExposedInMenuOrder() {
        XCTAssertEqual(GoldProvider.allCases, [.zheShang, .minSheng, .gongShang])
    }

    func testGongShangMetadata() {
        XCTAssertEqual(GoldProvider.gongShang.displayName, "工商积存金")
        XCTAssertEqual(GoldProvider.gongShang.shortName, "工银")
        XCTAssertEqual(
            GoldProvider.gongShang.url.absoluteString,
            "https://api.jdjygold.com/gw2/generic/produTools/h5/m/getGoldPrice?goldCode=ICBC-JCJ"
        )
    }

    func testDisplayNamesAndEndpointsAreUniquePerProvider() {
        let displayNames = Set(GoldProvider.allCases.map(\.displayName))
        let endpoints = Set(GoldProvider.allCases.map(\.url))
        XCTAssertEqual(displayNames.count, GoldProvider.allCases.count)
        XCTAssertEqual(endpoints.count, GoldProvider.allCases.count)
        // 简称用于状态栏与看板娘牌子，需短于完整名称（工银/工商积存金 不构成前缀关系）
        XCTAssertTrue(GoldProvider.allCases.allSatisfy { $0.shortName.count <= $0.displayName.count })
        XCTAssertEqual(GoldProvider.zheShang.shortName, "浙商")
        XCTAssertEqual(GoldProvider.minSheng.shortName, "民生")
    }

    func testProviderPersistenceRoundTripsAndKeepsLegacyValues() {
        for provider in GoldProvider.allCases {
            XCTAssertEqual(GoldProvider(rawValue: provider.rawValue), provider)
        }

        // 旧版本只写入过这两个值，升级后必须仍能还原
        XCTAssertEqual(GoldProvider(rawValue: "zheShang"), .zheShang)
        XCTAssertEqual(GoldProvider(rawValue: "minSheng"), .minSheng)
        XCTAssertNil(GoldProvider(rawValue: "notAProvider"))
    }

    /// 工商积存金与浙商积存金响应结构一致，共用京东金价解析。
    func testDecodesSharedJdGoldQuoteSchemaForGongShang() throws {
        let payload = Data(
            """
            {"resultData":{"code":"0000","data":{"uniqueCode":"ICBC-JCJ","name":"工商银行积存金","lastPrice":927.26,"raise":4.06,"raisePercent":0.0043977}},"resultCode":0}
            """.utf8
        )

        let response = try JSONDecoder().decode(JdGoldQuoteResponse.self, from: payload)
        let node = try XCTUnwrap(response.resultData?.data)
        XCTAssertEqual(node.lastPrice, 927.26)
        XCTAssertEqual(node.raise, 4.06)
        XCTAssertEqual(node.raisePercent, 0.0043977)

        // decodeJdGoldQuote 仅做解析，不触发网络请求
        let info = try GoldPriceService().decodeJdGoldQuote(from: payload)
        XCTAssertEqual(info.price, 927.26, accuracy: 0.0001)
        XCTAssertEqual(info.changeAmount, "4.06")
        XCTAssertEqual(info.changePercent, "0.43%")
        XCTAssertEqual(info.isNegative, false)
    }

    func testDecodeJdGoldQuoteTreatsMissingNodesAsEmptyQuote() throws {
        let payload = Data(#"{"resultData":{"code":"0000","success":true}}"#.utf8)
        let info = try GoldPriceService().decodeJdGoldQuote(from: payload)
        XCTAssertEqual(info.price, 0)
        XCTAssertEqual(info.changeAmount, "0.00")
        XCTAssertNil(info.isNegative)
    }
}
