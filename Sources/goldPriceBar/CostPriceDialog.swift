import AppKit

/// 每个数据源各自的买入成本价，用于按成本价判断红绿。
enum CostPriceStore {
    private static let keyPrefix = "costPrice."

    static func key(for provider: GoldProvider) -> String {
        keyPrefix + provider.rawValue
    }

    static func load(from defaults: UserDefaults = .standard) -> [GoldProvider: Double] {
        var prices: [GoldProvider: Double] = [:]
        for provider in GoldProvider.allCases {
            let value = defaults.double(forKey: key(for: provider))
            if value.isFinite, value > 0 {
                prices[provider] = value
            }
        }
        return prices
    }

    static func save(_ prices: [GoldProvider: Double], to defaults: UserDefaults = .standard) {
        for provider in GoldProvider.allCases {
            if let value = prices[provider], value.isFinite, value > 0 {
                defaults.set(value, forKey: key(for: provider))
            } else {
                defaults.removeObject(forKey: key(for: provider))
            }
        }
    }
}

/// 成本价设置窗口：三个数据源各一行，留空表示不设置。
@MainActor
enum CostPriceDialog {
    static func present(
        current: [GoldProvider: Double],
        title: String = "设置成本价",
        informative: String = "高于成本价显示红色，低于成本价显示绿色。留空表示不设置。"
    ) -> [GoldProvider: Double]? {
        var draft: [GoldProvider: String] = [:]
        for provider in GoldProvider.allCases {
            draft[provider] = current[provider].map { String(format: "%.2f", $0) } ?? ""
        }

        while true {
            var fields: [GoldProvider: NSTextField] = [:]
            let alert = makeAlert(title: title, informative: informative, draft: draft, fields: &fields)

            NSApp.activate(ignoringOtherApps: true)
            guard alert.runModal() == .alertFirstButtonReturn else { return nil }

            var prices: [GoldProvider: Double] = [:]
            var invalid: [String] = []
            for provider in GoldProvider.allCases {
                let text = fields[provider]?.stringValue
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                draft[provider] = text

                if text.isEmpty { continue }
                if let value = Double(text), value.isFinite, value > 0 {
                    prices[provider] = value
                } else {
                    invalid.append(provider.displayName)
                }
            }

            if invalid.isEmpty { return prices }

            let warning = NSAlert()
            warning.messageText = "成本价输入无效"
            warning.informativeText = "\(invalid.joined(separator: "、")) 需要填写大于 0 的数字，或留空表示不设置。"
            warning.addButton(withTitle: "重新填写")
            NSApp.activate(ignoringOtherApps: true)
            warning.runModal()
        }
    }

    private static func makeAlert(
        title: String,
        informative: String,
        draft: [GoldProvider: String],
        fields: inout [GoldProvider: NSTextField]
    ) -> NSAlert {
        let alert = NSAlert()
        alert.messageText = title
        alert.informativeText = informative
        alert.alertStyle = .informational
        alert.addButton(withTitle: "保存")
        alert.addButton(withTitle: "跳过")

        let rows = NSStackView()
        rows.orientation = .vertical
        rows.alignment = .leading
        rows.spacing = 8

        for provider in GoldProvider.allCases {
            let label = NSTextField(labelWithString: provider.displayName)
            label.font = .systemFont(ofSize: 12, weight: .medium)
            label.widthAnchor.constraint(equalToConstant: 108).isActive = true

            let field = NSTextField()
            field.placeholderString = "未设置"
            field.alignment = .right
            field.font = .monospacedDigitSystemFont(ofSize: 13, weight: .regular)
            field.stringValue = draft[provider] ?? ""
            field.widthAnchor.constraint(equalToConstant: 120).isActive = true
            fields[provider] = field

            let row = NSStackView(views: [label, field])
            row.orientation = .horizontal
            row.spacing = 12
            rows.addArrangedSubview(row)
        }

        rows.layoutSubtreeIfNeeded()
        let fitting = rows.fittingSize
        rows.frame = NSRect(
            x: 0,
            y: 0,
            width: max(240, fitting.width),
            height: max(CGFloat(GoldProvider.allCases.count) * 26, fitting.height)
        )
        alert.accessoryView = rows
        return alert
    }
}
