package com.oldterns.vilebot.handlers.user;

import com.oldterns.vilebot.Vilebot;
import com.oldterns.vilebot.db.KarmaDB;
import com.oldterns.vilebot.util.BaseNick;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KarmaRoll
    extends ListenerAdapter
{
    private static final Pattern rollPattern = Pattern.compile( "!roll(?: for|)(?: +([0-9]+)|)" );

    private static final Pattern cancelPattern = Pattern.compile( "!roll ?cancel" );

    private static final int UPPER_WAGER = 10;

    private RollGame currentGame;

    private final Object currentGameMutex = new Object();

    @Override
    public void onGenericMessage( final GenericMessageEvent event )
    {
        String text = event.getMessage();

        Matcher rollMatcher = rollPattern.matcher( text );
        Matcher cancelMatcher = cancelPattern.matcher( text );

        if ( rollMatcher.matches() )
            userHelp( event, rollMatcher );
        if ( cancelMatcher.matches() )
            manualCancel( event );
    }

    // @Handler
    private void userHelp( GenericMessageEvent event, Matcher rollMatcher )
    {
        String sender = BaseNick.toBaseNick( event.getUser().getNick() );

        // Infers ircChannel1 in JSON is #thefoobar for production Vilebot
        if ( !( event instanceof MessageEvent )
            || !( (MessageEvent) event ).getChannel().getName().equals( Vilebot.getConfig().get( "ircChannel1" ) ) )
        {
            event.respondWith( "You must be in " + Vilebot.getConfig().get( "ircChannel1" )
                + " to make or accept wagers." );
            return;
        }

        String rawWager = rollMatcher.group( 1 );

        synchronized ( currentGameMutex )
        {
            if ( currentGame == null )
            {
                // No existing game

                if ( rawWager == null )
                {
                    // No game exists, but the user has not given a karma wager, so default to 10
                    rawWager = "10";
                }

                // Check the wager value is in the acceptable range.
                int wager = Integer.parseInt( rawWager );
                Integer senderKarma = KarmaDB.getNounKarma( sender );
                senderKarma = senderKarma == null ? 0 : senderKarma;

                if ( !validWager( wager, senderKarma ) )
                {
                    event.respondWith( wager
                        + " isn't a valid wager. Must be greater than 0. If you wager is larger than " + UPPER_WAGER
                        + " you must have at least as much karma as your wager." );
                }
                else
                {
                    // Acceptable wager, start new game

                    currentGame = new RollGame( sender, wager );
                    event.respondWith( sender + " has rolled with " + wager + " karma points on the line.  Who's up?" );
                }
            }
            else
            {
                // A game exists

                if ( rawWager != null )
                {
                    // A game exists, but the user has given a karma wager, probably an error.

                    String str = "A game is already active; started by " + currentGame.getFirstPlayerNick() + " for "
                        + currentGame.getWager() + " karma. Use !roll to accept.";
                    event.respondWith( str );
                }
                else
                {
                    // A game exists, and no karma value was given. User is accepting the active wager/game.

                    // The user that started a game cannot accept it
                    if ( currentGame.getFirstPlayerNick().equals( sender ) )
                    {
                        event.respondWith( "You can't accept your own wager." );
                    }
                    else
                    {
                        GameResults result = currentGame.setSecondPlayer( sender );

                        String firstPlayer = currentGame.getFirstPlayerNick();
                        int firstRoll = result.getFirstPlayerRoll();
                        String secondPlayer = currentGame.getSecondPlayerNick();
                        int secondRoll = result.getSecondPlayerRoll();

                        String winner = result.getWinnerNick();
                        String loser = result.getLoserNick();
                        int deltaKarma = currentGame.getWager();

                        StringBuilder sb = new StringBuilder();
                        sb.append( "Results: " );
                        sb.append( firstPlayer );
                        sb.append( " rolled " );
                        sb.append( firstRoll );
                        sb.append( ", and " );
                        sb.append( secondPlayer );
                        sb.append( " rolled " );
                        sb.append( secondRoll );
                        sb.append( ". " );

                        if ( winner != null && loser != null )
                        {
                            sb.append( winner );
                            sb.append( " takes " );
                            sb.append( deltaKarma );
                            sb.append( " from " );
                            sb.append( loser );
                            sb.append( "!!!" );

                            KarmaDB.modNounKarma( winner, deltaKarma );
                            KarmaDB.modNounKarma( loser, -1 * deltaKarma );
                        }
                        else
                        {
                            sb.append( "A tie!" );
                        }

                        event.respondWith( sb.toString() );

                        // Reset
                        currentGame = null;

                        event.respondWith( "Play again?" );
                    }
                }
            }
        }
    }

    /**
     * A valid wager is one that meets the following standards: 1. Is greater than 0. 2. If it is greater than 10, then
     * the user's karma is equal to or greater than the wager. The reasoning behind this is to have some base amount of
     * karma that user's can bet with, but also provide a way of betting large amounts of karma. To avoid destroying the
     * karma economy users cannot bet more than their current amount of karma.
     * 
     * @param wager the amount wagered
     * @param senderKarma the wagerer's karma
     */
    private boolean validWager( int wager, int senderKarma )
    {
        return !( wager > 10 ) && ( wager > 0 ) || ( wager > 10 ) && ( senderKarma >= wager );
    }

    private void manualCancel( GenericMessageEvent event )
    {
        if ( !currentGame.getFirstPlayerNick().equals( event.getUser().getNick() ) )
        {
            event.respondWith( "Only " + currentGame.getFirstPlayerNick() + " may cancel this game." );
            return;
        }
        synchronized ( currentGameMutex )
        {
            currentGame = null;
        }
        event.respondWith( "Roll game cancelled." );
    }

    private static class RollGame
    {
        private final int wager;

        private String firstPlayer;

        private String secondPlayer;

        RollGame( String firstPlayerNick, int wager )
        {
            if ( firstPlayerNick == null )
                throw new IllegalArgumentException( "firstPlayerNick can't be null" );

            this.firstPlayer = firstPlayerNick;
            this.wager = wager;
        }

        GameResults setSecondPlayer( String secondPlayerNick )
        {
            if ( secondPlayerNick == null )
                throw new IllegalArgumentException( "secondPlayerNick can't be null" );

            if ( secondPlayer == null )
                secondPlayer = secondPlayerNick;
            else
                throw new StateViolation( "Can't set the second player twice" );

            return new GameResults( firstPlayer, secondPlayer );
        }

        String getFirstPlayerNick()
        {
            return firstPlayer;
        }

        String getSecondPlayerNick()
        {
            return secondPlayer;
        }

        int getWager()
        {
            return wager;
        }
    }

    private static class GameResults
    {
        private static final int SIDES_OF_DIE = 6;

        private static final Random random = new SecureRandom();

        private final String firstPlayer;

        private final String secondPlayer;

        private Integer firstPlayerRoll = null;

        private Integer secondPlayerRoll = null;

        GameResults( String firstPlayer, String secondPlayer )
        {
            this.firstPlayer = firstPlayer;
            this.secondPlayer = secondPlayer;

            doRolls();
        }

        /**
         * @return The nick of the winning player, or null on a tie
         */
        String getWinnerNick()
        {
            if ( firstPlayerRoll > secondPlayerRoll )
                return firstPlayer;
            else if ( secondPlayerRoll > firstPlayerRoll )
                return secondPlayer;
            else
                return null; // Tied
        }

        /**
         * @return The nick of the losing player, or null on a tie
         */
        String getLoserNick()
        {
            if ( firstPlayerRoll < secondPlayerRoll )
                return firstPlayer;
            else if ( secondPlayerRoll < firstPlayerRoll )
                return secondPlayer;
            else
                return null; // Tied
        }

        /**
         * @return The value of the dice roll the first player got
         */
        int getFirstPlayerRoll()
        {
            return firstPlayerRoll;
        }

        /**
         * @return The value of the dice roll the second player got
         */
        int getSecondPlayerRoll()
        {
            return secondPlayerRoll;
        }

        private void doRolls()
        {
            firstPlayerRoll = random.nextInt( SIDES_OF_DIE ) + 1;
            secondPlayerRoll = random.nextInt( SIDES_OF_DIE ) + 1;
        }
    }

    private static class StateViolation
        extends RuntimeException
    {
        private static final long serialVersionUID = -7530159745349382310L;

        StateViolation( String message )
        {
            super( message );
        }
    }
}
