package com.imc.intern.trading;

/**
 * Structure to represent a market
 * Created by imc on 11/01/2017.
 */
public class Market {
    private String market_name;
    private double bids_price;
    private int bids_volume;
    private double asks_price;
    private int asks_volume;
    private int position;

    //TODO: want to store more than just top level

    public Market(String name){
        this.market_name = name;
        position = 0;
    }

    public void updateBids(double price, int volume){
        bids_price = price;
        bids_volume = volume;
    }

    public void updateAsks(double price, int volume){
        asks_price = price;
        asks_volume = volume;
    }

    public void updatePosition(int change){
        position += change;
    }

    public String getMarket_name(){return market_name;}

    public double getBidsPrice(){return bids_price;}

    public double getAsksPrice(){return asks_price;}

    public int getBidsVolume(){return bids_volume;}

    public int getAsksVolume(){return asks_volume;}

    public int getPosition(){return position;}
}
