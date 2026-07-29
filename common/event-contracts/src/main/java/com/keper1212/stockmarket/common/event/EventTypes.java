package com.keper1212.stockmarket.common.event;

public final class EventTypes {

    public static final String ORDER_ACCEPTED = "ORDER_ACCEPTED";
    public static final String ORDER_CANCEL_REQUESTED = "ORDER_CANCEL_REQUESTED";
    public static final String ASSET_HOLD_REQUESTED = "ASSET_HOLD_REQUESTED";
    public static final String ASSET_HOLD_SUCCEEDED = "ASSET_HOLD_SUCCEEDED";
    public static final String ASSET_HOLD_FAILED = "ASSET_HOLD_FAILED";
    public static final String TRADE_EXECUTED = "TRADE_EXECUTED";
    public static final String ORDER_CANCELED = "ORDER_CANCELED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String MARKET_TRADE_SETTLED = "MARKET_TRADE_SETTLED";
    public static final String MARKET_ORDERBOOK_CHANGED = "MARKET_ORDERBOOK_CHANGED";

    private EventTypes() {
    }
}
