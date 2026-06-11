import csv
import os
import random
from pathlib import Path
from uuid import uuid4

from locust import HttpUser, between, task


BASE_DIR = Path(__file__).resolve().parent
USERS_CSV = BASE_DIR / "users.csv"
FALLBACK_STOCK_PRICES = {
    "HYU-MOTOR": 10000,
    "SAM-ELEC": 5000,
    "SK-HYNIX": 20000,
}
STOCK_CODES = list(FALLBACK_STOCK_PRICES.keys())
MARKETDATA_HOST = os.getenv("MARKETDATA_HOST", "").rstrip("/")
PRICE_RANGE_RATE = 0.05
PRICE_UNIT = 100
MIN_ORDER_QUANTITY = 1
MAX_ORDER_QUANTITY = 10


def load_users():
    with USERS_CSV.open(newline="", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def round_down_to_unit(value):
    return int(value // PRICE_UNIT) * PRICE_UNIT


def round_up_to_unit(value):
    return int((value + PRICE_UNIT - 1) // PRICE_UNIT) * PRICE_UNIT


def random_price_from_current_price(current_price):
    min_price = round_up_to_unit(current_price * (1 - PRICE_RANGE_RATE))
    max_price = round_down_to_unit(current_price * (1 + PRICE_RANGE_RATE))
    if max_price < min_price:
        return max(PRICE_UNIT, round_up_to_unit(current_price))

    steps = (max_price - min_price) // PRICE_UNIT
    return min_price + (random.randint(0, steps) * PRICE_UNIT)


def random_quantity():
    return random.randint(MIN_ORDER_QUANTITY, MAX_ORDER_QUANTITY)


class StockMarketUser(HttpUser):
    wait_time = between(3, 15)

    def on_start(self):
        self.access_token = None
        self.user = random.choice(load_users())
        self.current_prices = FALLBACK_STOCK_PRICES.copy()
        self.login()
        self.refresh_current_prices()

    def login(self):
        response = self.client.post(
            "/api/v1/auth/login",
            json={
                "email": self.user["email"],
                "password": self.user["password"],
            },
            name="POST /api/v1/auth/login",
        )
        if response.ok:
            self.access_token = response.json().get("accessToken")

    def auth_headers(self):
        if not self.access_token:
            return {}
        return {"Authorization": f"Bearer {self.access_token}"}

    def marketdata_url(self, path):
        if not MARKETDATA_HOST:
            return path
        return f"{MARKETDATA_HOST}{path}"

    def refresh_current_prices(self):
        response = self.client.get(self.marketdata_url("/api/v1/stocks"), name="GET /api/v1/stocks")
        if not response.ok:
            return

        try:
            stocks = response.json().get("stocks", [])
        except ValueError:
            return

        for stock in stocks:
            stock_code = stock.get("stockCode")
            current_price = stock.get("currentPrice")
            if stock_code in STOCK_CODES and isinstance(current_price, int) and current_price > 0:
                self.current_prices[stock_code] = current_price

    def random_price(self, stock_code):
        current_price = self.current_prices.get(stock_code, FALLBACK_STOCK_PRICES[stock_code])
        return random_price_from_current_price(current_price)

    @task(70)
    def get_stocks(self):
        self.refresh_current_prices()

    @task(25)
    def get_orderbook(self):
        stock_code = random.choice(STOCK_CODES)
        self.client.get(
            self.marketdata_url(f"/api/v1/stocks/{stock_code}/orderbook"),
            name="GET /api/v1/stocks/{stockCode}/orderbook",
        )

    @task(5)
    def place_order(self):
        if random.random() < 0.5:
            self.place_buy_order()
        else:
            self.place_sell_order()

    def place_buy_order(self):
        stock_code = random.choice(STOCK_CODES)
        self.client.post(
            "/api/v1/orders",
            json={
                "clientOrderId": f"locust-buy-{uuid4()}",
                "stockCode": stock_code,
                "orderType": "BUY",
                "price": self.random_price(stock_code),
                "quantity": random_quantity(),
            },
            headers=self.auth_headers(),
            name="POST /api/v1/orders BUY",
        )

    def place_sell_order(self):
        stock_code = random.choice(STOCK_CODES)
        self.client.post(
            "/api/v1/orders",
            json={
                "clientOrderId": f"locust-sell-{uuid4()}",
                "stockCode": stock_code,
                "orderType": "SELL",
                "price": self.random_price(stock_code),
                "quantity": random_quantity(),
            },
            headers=self.auth_headers(),
            name="POST /api/v1/orders SELL",
        )
