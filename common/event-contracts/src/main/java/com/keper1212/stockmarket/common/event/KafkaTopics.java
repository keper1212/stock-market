package com.keper1212.stockmarket.common.event;

public final class KafkaTopics {

    public static final String ORDER_EVENTS = "order-events";
    public static final String TRADE_EVENTS = "trade-events";
    public static final String ASSET_COMMANDS = "asset-commands";
    public static final String ASSET_EVENTS = "asset-events";
    public static final String MARKET_EVENTS = "market-events";

    private KafkaTopics() {
    }
}
