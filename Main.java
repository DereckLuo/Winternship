package com.imc.intern.trading;

import com.imc.intern.exchange.client.ExchangeClient;
import com.imc.intern.exchange.datamodel.Side;
import com.imc.intern.exchange.datamodel.api.*;

import java.time.Clock;

public class Main
{
    private static final String EXCHANGE_URL = "tcp://54.227.125.23:61616";//tcp://wintern.imc.com:61616";
    private static final String USERNAME = "dluo";
    private static final String PASSWORD = "cry piano folks control";
    /*
    private static final String BOOK1 = "DLU.TACO";
    private static final String BOOK2 = "DLU.BEEF";
    private static final String BOOK3 = "DLU.TORT";
    */

    private static final String BOOK1 = "TACO";
    private static final String BOOK2 = "BEEF";
    private static final String BOOK3 = "TORT";


    public static void main(String[] args) throws Exception
    {


        ExchangeClient client = ExchangeClient.create(EXCHANGE_URL, Account.of(USERNAME), PASSWORD);

        client.start();

        Market TACO = new Market(BOOK1);
        Market BEEF = new Market(BOOK2);
        Market TORT = new Market(BOOK3);

        Strategies opportunity_checker = new Strategies(TACO, BEEF, TORT);

        Bookmanage taco = new Bookmanage(BOOK1,TACO,opportunity_checker);
        Bookmanage beef = new Bookmanage(BOOK2,BEEF,opportunity_checker);
        Bookmanage tort = new Bookmanage(BOOK3,TORT,opportunity_checker);

        //Subscribe for three different books
        taco.subscribe(Symbol.of(BOOK1), client);
        beef.subscribe(Symbol.of(BOOK2), client);
        tort.subscribe(Symbol.of(BOOK3), client);


    }
}
