package com.imc.intern.trading;

import java.util.*;

import com.imc.intern.exchange.client.ExchangeClient;
import com.imc.intern.exchange.datamodel.api.*;
import com.imc.intern.exchange.datamodel.Side;
import com.imc.intern.exchange.datamodel.jms.ExposureUpdate;
import com.imc.intern.exchange.datamodel.api.Error;
import com.imc.intern.exchange.views.ExchangeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Bookmanage class:
 * Subscribe to different tokens. keep tracking of different token books
 * which keep in track the entire book, keep it updated and perform certain actions
 * Created by imc on 10/01/2017.
 */
public class Bookmanage {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bookmanage.class);

    private Symbol book;     //the name of the book
    private TreeMap<Double, Integer> bids_tree = new TreeMap<>();    //two heap to map price to volume
    private TreeMap<Double, Integer> asks_tree = new TreeMap<>();
    private double average;    //the average of the market, considered as the estimated real value
    private double buy_price;
    private double sell_price;
    private double sellposition = 1;
    private double buyposition = 1;  //between 0 to 1, 0 means don't want to do this action, only do at really really good price
    private Market market;    //access to other market
    private Strategies strategy;

    public Bookmanage(String input, Market book_market, Strategies opportunity){
        book = Symbol.of(input);
        market = book_market;
        strategy = opportunity;
    }


    public void subscribe(Symbol book, ExchangeClient client){
        client.getExchangeView().subscribe(book, new OrderBookHandler() {
            public void handleRetailState(RetailState retailState) {
                //LOGGER.info("~~~~~~~~~~~~~~Retailstate hanlder~~~~~~~~~~~~" + book.toString());
                updateRS(retailState, client.getExchangeView());
            }

            @Override
            public void handleOwnTrade(OwnTrade trade) {
                LOGGER.info("~~~~~~~~~~~~~~~~~~~Owntrade handler~~~~~~~~~~~~~~");
                OwnTradehandle(trade);
            }

            @Override
            public void handleExposures(ExposureUpdate exposures) {
                LOGGER.info("~~~~~~~~~~~~~~~~~~Exposure handler~~~~~~~~~~~~");
                Exposurehandle(exposures);
            }

            @Override
            public void handleTrade(Trade trade) {
                //LOGGER.info("~~~~~~~~~~~~~~~~~~Trade handler~~~~~~~~~~~~~~~");
                Tradehandle(trade);
            }

            @Override
            public void handleError(Error error) {
                LOGGER.info("~~~~~~~~~~~~~~~~~~Error handler~~~~~~~~~~~~~~");
                Errorhandle(error);
            }
        });
    }

    /**
     * Function: updateMarket
     */
    public void updateMarket(){
        if(!bids_tree.isEmpty()){
            market.updateBids(bids_tree.descendingMap().firstKey(), bids_tree.descendingMap().firstEntry().getValue());
        }
        if(!bids_tree.isEmpty()){
            market.updateAsks(asks_tree.firstKey(), asks_tree.firstEntry().getValue());
        }
    }

    /**
     * Function updteRS: called when every retail states gives back
     * Given a retail state, updates the local tracking variables
     */
    public void updateRS(RetailState RS, ExchangeView exchangeView){
        LOGGER.info(RS.toString());

        int bids_size = RS.getBids().size();
        int asks_size = RS.getAsks().size();

        for(int i = 0; i < bids_size; i++){
            bids_tree.put(RS.getBids().get(i).getPrice(), RS.getBids().get(i).getVolume());
        }

        for(int i = 0; i < asks_size; i++){
            asks_tree.put(RS.getAsks().get(i).getPrice(), RS.getAsks().get(i).getVolume());
        }

        average = computeAverage();
        updateMarket();
        buyposition = strategy.buy_position(market);
        sellposition = strategy.sell_position(market);
        computeOffset();
        strategy.updateActionTime();


        if(strategy.checkFlatting() && strategy.getQuoting()){ //on final flatting process
            strategy.tryFlatting(book.toString(), market, bids_tree, asks_tree, average, exchangeView);
            strategy.printoutPosition();
        }
        else{
            //checking trade strategies
            if(book.toString().equals("TACO")){
                strategy.checkHedgeTrade(book.toString(), buyposition, sellposition, bids_tree, asks_tree, exchangeView);
            }
            strategy.checkImmediateTrade(book.toString(), market.getPosition(), buyposition, sellposition, buy_price, sell_price, bids_tree, asks_tree, exchangeView);
        }

    }

    /**
     * Function: comptueAverage
     * Compute the average of the current product, using as the expected value
     */
    public double computeAverage(){
        double ret = 0; //default average value
        if(bids_tree.size() != 0 && asks_tree.size() != 0){
            ret = (bids_tree.descendingMap().firstEntry().getKey() + asks_tree.firstKey())/2;
        }
        return ret;
    }

    /**
     * Function computeOffset
     * Compute the value that good to sell and buy
     */
    public void computeOffset() {
        if(!asks_tree.isEmpty()){
            sell_price = average + (2/3)*sellposition * (asks_tree.firstKey() - average);
        }
        else{sell_price = -1;}
        if(!bids_tree.isEmpty()){
            buy_price = average - (2/3)*buyposition * (average - bids_tree.descendingMap().firstKey());
        }
        else{buy_price = -1;}
    }

    /**
     * Function updatePosition
     */
    public void updatePosition(OwnTrade trade){
        Side type = trade.getSide();
        int volume = trade.getVolume();
        if(type == Side.BUY){
            LOGGER.info("buy position update : " + Integer.toString(volume));
            if(book.toString() == "TACO"){
                strategy.updateTaco(volume);
            }
            else market.updatePosition(volume);
        }
        else{
            LOGGER.info("sell position update : " + Integer.toString(volume));
            if(book.toString() == "TACO"){
                strategy.updateTaco(-1*volume);
            }
            else market.updatePosition(-1*volume);
        }
        buyposition = strategy.buy_position(market);
        sellposition = strategy.sell_position(market);
        LOGGER.info("-----current book is : " + market.getMarket_name() + "------");
        LOGGER.info("buyposition is : " + Double.toString(buyposition));
        LOGGER.info("sellposition is : " + Double.toString(sellposition));
    }

    /**
     * Function OwnTradehandle:
     *  Handling your own trade
     *  Given the own trade structure
     */
    public void OwnTradehandle(OwnTrade trade){
        System.out.println(trade);
        updatePosition(trade);
        strategy.printoutPosition();

    }

    /**
     * Function Exposurehandle:
     *  Handling the exposure trade
     *  Update the local tracking structure
     */
    public void Exposurehandle(ExposureUpdate exposures){
        System.out.println(exposures.getExposures());
    }

    /**
     * Function Tradehandle:
     *  Handling the universal trade
     */
    public void Tradehandle(Trade trade){
        LOGGER.info(trade.toString());
    }

    /**
     * Function Errorhandle:
     *  Handling the error output
     */
    public void Errorhandle(Error error){
        LOGGER.error(error.toString());
    }



}
