package com.imc.intern.trading;

import com.imc.intern.exchange.views.ExchangeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.imc.intern.exchange.datamodel.api.*;
import com.imc.intern.exchange.datamodel.Side;

import java.time.Clock;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Strategy Class
 * Which calling from the book manager and check if there are any opportunities and react on it
 * Created by imc on 11/01/2017.
 */
public class Strategies {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bookmanage.class);
    private static Market Taco, Beef, Tort;    //three market
    private static int max_trade = 50;
    private static final int initMaxPosition = 50;
    private static final int initMinPosition = -50;
    private static int max_position = 50;
    private static int min_position = -50;
    private static long remainTime = 0;
    private static Clock clock = Clock.systemUTC();
    private static long endTime = 0;
    private static int exchangeTime = 3;
    private static long exchangeTimeMill;
    private static boolean flatting = false;
    private static boolean quoting = true;
    private static long timeCheck;
    private static int actionTime = 3;
    private static long timePeriod = 10000;

    public Strategies(Market taco, Market beef, Market tort){
        Taco = taco;
        Beef = beef;
        Tort = tort;
        exchangeTimeMill = TimeUnit.MINUTES.toMillis(exchangeTime);
        endTime = now() + exchangeTimeMill;
        remainTime = endTime - now();
        timeCheck = now();
    }

    private static long now() {
        return clock.instant().toEpochMilli();
    }

    public static void updateActionTime(){
        long now_time = now();
        if(now_time - timeCheck >= timePeriod){
            LOGGER.info("action reset !!!!!!!!!!!!!!!!!!!!");
            timeCheck = now_time;
            actionTime = 3;
        }
    }

    public static void updateFlatRate(){
        remainTime = endTime - now();

        if(remainTime < 0.75*exchangeTimeMill && remainTime > 0.5*exchangeTimeMill){
            max_position = (int)0.75*initMaxPosition; min_position = (int)0.75*initMinPosition;
            assert min_position < 0;
        }
//        else if(remainTime < 0.5*exchangeTimeMill && remainTime > 0.25*exchangeTimeMill){
//            max_position = (int)0.5*initMaxPosition; min_position = (int)0.5*initMinPosition;
//            assert min_position < 0;
//        }
//        else if(remainTime < 0.25*exchangeTimeMill && remainTime > 0.1*exchangeTimeMill){
//            max_position = (int)0.25*initMaxPosition; min_position = (int)0.25*initMinPosition;
//            assert min_position < 0;
//        }
        else if(remainTime < 0.2*exchangeTimeMill){
            LOGGER.info("!!!!!!!!!!!!!!!!!!! LVL4 !!!!!!!!!!!!!!!!!!!!!");
            flatting = true;
        }
    }

    /**
     * Function checkflatting
     */
    public static boolean checkFlatting(){
        updateFlatRate();
        return flatting;
    }

    /**
     * Function getQuoting
     */
    public static boolean getQuoting(){
        return quoting;
    }

    /**
     * Function tryflatting
     *  flatting with the average price
     */
    public static void tryFlatting(String book, Market market, TreeMap<Double, Integer> bids_tree, TreeMap<Double, Integer> asks_tree, double average, ExchangeView exchangeView){
        LOGGER.info("------------ flatting kicking in -------------");
        int cur_position = market.getPosition();

        if(cur_position > 0){
            double bid_price = bids_tree.descendingMap().firstKey();
            int bid_volume = cur_position;
            exchangeView.createOrder(Symbol.of(book), bid_price, bid_volume, OrderType.GOOD_TIL_CANCEL, Side.SELL);
        }
        else if(cur_position < 0){
            double ask_price = asks_tree.firstKey();
            int ask_volume = cur_position;
            exchangeView.createOrder(Symbol.of(book), ask_price, ask_volume, OrderType.GOOD_TIL_CANCEL, Side.BUY);
        }
        quoting = false;



//        if(market.getPosition() != 0){//not flat
//            if(cur_position > 0){ // need to sell
//                double bid_price = bids_tree.descendingMap().firstKey();
//                int bid_volume = Math.min(cur_position, bids_tree.descendingMap().firstEntry().getValue());
//                if(bid_volume != 0){
//                    createIOC(book, bid_price, bid_volume,OrderType.IMMEDIATE_OR_CANCEL,Side.SELL,exchangeView);
//                }
//            }
//            else{//need to buy
//                double ask_price = asks_tree.descendingMap().firstKey();
//                int ask_volume = Math.min(cur_position, asks_tree.firstEntry().getValue());
//                if(ask_volume != 0){
//                    createIOC(book, ask_price, ask_volume, OrderType.IMMEDIATE_OR_CANCEL, Side.BUY, exchangeView);
//                }
//            }
//        }
    }

    /**
     * Function checkHedgeTrade:
     *  Checking opportunities for hedge trade, immediate react on it if true.
     */
    public static void checkHedgeTrade(String book, double buyposition, double sellposition, TreeMap<Double, Integer> bids_tree, TreeMap<Double, Integer> asks_tree, ExchangeView exchangeView){
        double cross_sell, cross_buy;   //price other markets are selling and buying at
        int sell_volume, buy_volume;    //volume other markets are selling and buying at

        if(book.toString().equals("TACO")){
            cross_sell = Beef.getAsksPrice() + Tort.getAsksPrice();
            cross_buy = Beef.getBidsPrice() + Tort.getBidsPrice();
            sell_volume = Math.min(Beef.getAsksVolume(),Tort.getAsksVolume());
            buy_volume = Math.min(Beef.getBidsVolume(), Tort.getBidsVolume());
        }
        else{
            cross_sell = 0; cross_buy = 0; sell_volume = 0; buy_volume = 0;
            LOGGER.error("~~~~~~~cross trade failed ~~~~~~~");
        }

        if(book.toString().equals("TACO")){
            if(!bids_tree.isEmpty() && cross_sell < bids_tree.descendingMap().firstKey()){  //other market are selling lower than local buy, want to sell as much taco as i can
                int min_volume = Math.min(sell_volume, bids_tree.descendingMap().firstEntry().getValue());
                min_volume = Math.min(min_volume, max_position);
                if(min_volume > 0 && (int)sellposition != 0){
                    LOGGER.info("~~~~~~CROSS TRADE ~~~~~SELL~~~");
                    createIOC(book, bids_tree.descendingMap().firstKey(), min_volume, OrderType.IMMEDIATE_OR_CANCEL, Side.SELL, exchangeView);
                }
            }
            else if(!asks_tree.isEmpty() && cross_buy > asks_tree.firstKey()){ //other market are buying higher than local sell, want to buy as much taco as i can
                int min_volume = Math.min(buy_volume, asks_tree.firstEntry().getValue());
                min_volume = Math.min(min_volume, max_position);
                if(min_volume > 0 && (int)buyposition != 0){
                    LOGGER.info("~~~~~~CROSS TRADE ~~~~~~BUY~~~~~");
                    createIOC(book, asks_tree.firstKey(), min_volume, OrderType.IMMEDIATE_OR_CANCEL, Side.BUY, exchangeView);
                }
            }
        }
    }


    /**
     * Function checkImmediateTrade:
     *  check opportunity for immediate trade and act on it.
     */
    public static void checkImmediateTrade(String book, int position, double buyposition, double sellposition, double buy_price, double sell_price, TreeMap<Double, Integer> bids_tree, TreeMap<Double, Integer> asks_tree, ExchangeView exchangeView){
        LOGGER.info(book.toString() + " " + Integer.toString(position) + " " + Double.toString(buyposition) + " " + Double.toString(sellposition) + " " + Double.toString(buy_price) + " " + Double.toString(sell_price) + " ");
        if(bids_tree.size()!=0 && position >= 0 && (int)sellposition != 0){
            double bit_price = bids_tree.descendingMap().firstKey();
            int bit_volume = Math.min(bids_tree.descendingMap().firstEntry().getValue(), max_position);
            if(position != 0){
                bit_volume = Math.min(bit_volume, position);
            }
            if(bit_volume > 0 && bit_price >= (sell_price) && sell_price != -1){   //case bit price above average -- time to sell
                createIOC(book, bit_price, bit_volume, OrderType.IMMEDIATE_OR_CANCEL, Side.SELL, exchangeView);
            }
        }
        if(asks_tree.size() != 0 && position <= 0 && (int)buyposition != 0){
            double ask_price = asks_tree.firstKey();
            int ask_volume = Math.min(max_position, asks_tree.firstEntry().getValue());
            if(position != 0){
                ask_volume = Math.min(ask_volume, Math.abs(position));
            }
            if(ask_volume > 0 && ask_price <= (buy_price) && buy_price != -1){               //case ask price below average -- time to buy
                createIOC(book, ask_price, ask_volume, OrderType.IMMEDIATE_OR_CANCEL, Side.BUY, exchangeView);
            }
        }
    }

    /**
     * Function createIOC
     *  function to create an IOC based on the parameters
     */
    public static void createIOC(String book, double price, int volume, OrderType type, Side side, ExchangeView exchangeView){
        if(actionTime > 0 && volume > 0){
        //if(IOC.tryAcquire() && volume > 0){
            exchangeView.createOrder(Symbol.of(book), price, Math.min(max_trade,volume), type, side);
            actionTime -= 1;
            LOGGER.info("~~~~~~IMMEDIATE TRADE ~~~~~~~" + side.toString());
        }
    }


    /**
     * Function createGTC
     *  function to create or cancle GTC on the parameters
     *  action: false -- cancel
     *          true -- create
     */
    public static void createGTC(String book, double price, int volume, OrderType type, Side side, ExchangeView exchangeView, long id, boolean action){
        if(actionTime > 0 && volume > 0){
            if(action){
                exchangeView.createOrder(Symbol.of(book), price, Math.min(max_trade,volume), type, side);
            }
            else{
                exchangeView.cancelOrder(Symbol.of(book), id);
            }
        }
    }

    /**
     * Function buy_position
     *  function to calculate position to buy stuff
     *  given a number between 0 and 1
     */
    public static double buy_position(Market market){
        int market_volume = 0;

        if(market.getMarket_name().equals("TACO")){
            market_volume = Beef.getPosition() + Tort.getPosition();
        }
        else{
            market_volume = market.getPosition();
        }

        LOGGER.info(market.getMarket_name() + " buy market volume is : " + Integer.toString(market_volume));

        if (market_volume > max_position){  //over max long position
            return 0;
        }
        else return 1;
    }

    /**
     * Function sell_position
     *  function to calculate position to sell stuff
     *  given a number between 0 and 1
     */
    public static double sell_position(Market market){
        int market_volume = 0;

        if(market.getMarket_name().equals("TACO")){
            market_volume = Beef.getPosition() + Tort.getPosition();
        }
        else{
            market_volume = market.getPosition();
        }
        LOGGER.info(market.getMarket_name() + " sell market volume is : " + Integer.toString(market_volume));

        if(market_volume < min_position){ //over min short position
            return 0;
        }
        else return 1;
    }

    /**
     * Function updateTaco
     */
    public void updateTaco(int volume){
        Beef.updatePosition(volume);
        Tort.updatePosition(volume);
    }

    /**
     * Function printoutPosition
     */
    public void printoutPosition(){
        LOGGER.info("Market is : " + Taco.getMarket_name());
        int taco_position = Taco.getPosition();
        LOGGER.info("~~~~~Position at : " + Integer.toString(taco_position));
        LOGGER.info("Market is : " + Beef.getMarket_name());
        int beef_position = Beef.getPosition();
        LOGGER.info("~~~~~Position at : " + Integer.toString(beef_position));
        LOGGER.info("Market is : " + Tort.getMarket_name());
        int tort_position = Tort.getPosition();
        LOGGER.info("~~~~~Position at : " + Integer.toString(tort_position));
    }

}
