package com.oldterns.vilebot.util;

import com.google.common.collect.ImmutableSortedSet;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

/**
 * Reverse all nicks in messages
 */
public class MangleNicks
{

    public static String mangleNicks( MessageEvent event, String message )
    {
        return mangleNicks( event.getChannel().getUsersNicks(), message );
    }

    public static String mangleNicks( JoinEvent event, String message )
    {
        return mangleNicks( event.getChannel().getUsersNicks(), message );
    }

    public static String mangleNicks( GenericMessageEvent event, String message )
    {
        return mangleNicks( ImmutableSortedSet.of(), message );
    }

    private static String mangleNicks( ImmutableSortedSet<String> nicks, String message )
    {

        if ( nicks.isEmpty() )
        {
            return message;
        }

        StringBuilder reply = new StringBuilder();
        for ( String word : message.split( " " ) )
        {
            reply.append( " " );
            reply.append( inside( nicks, word ) ? mangled( word ) : word );
        }
        return reply.toString().trim();
    }

    private static String mangled( String word )
    {
        return new StringBuilder( word ).reverse().toString();
    }

    private static boolean inside( ImmutableSortedSet<String> nicks, String word )
    {
        for ( String nick : nicks )
        {
            if ( word.contains( nick ) )
            {
                return true;
            }
        }
        return false;
    }
}