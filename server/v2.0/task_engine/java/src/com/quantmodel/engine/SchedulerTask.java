package com.quantmodel.engine;

import java.io.ByteArrayOutputStream;

import java.util.Date;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import java.text.SimpleDateFormat;

import org.dom4j.Node;

import org.zeromq.ZMQ;

public abstract class SchedulerTask implements Runnable
{
    final Object lock = new Object();

    int state = VIRGIN;
    static final int VIRGIN = 0;
    static final int SCHEDULED = 1;
    static final int CANCELLED = 2;

    protected TimerTask timerTask;
    protected ScheduleIterator iterator;
    protected String name;

    protected SchedulerTask() { }

    public String getName() { return name; }

    public abstract void init( Node node ) throws Exception;

    public abstract void run();

    public ScheduleIterator getIterator() { return iterator; }

    public boolean cancel()
    {
        synchronized( lock )
        {
            if( timerTask != null )
            {
                timerTask.cancel();
            }

            boolean result = ( state == SCHEDULED );
            state = CANCELLED;
            return result;
        }
    }

    public long scheduledExecutionTime()
    {
        synchronized( lock )
        {
            return timerTask == null ? 0 : timerTask.scheduledExecutionTime();
        }
    }

    protected java.sql.Connection getConnection( final String db )
        throws Exception
    {
        return java.sql.DriverManager.getConnection(
            "jdbc:mysql://localhost/" + db + "?user=root&password=password" );
    }

    protected void setScheduleIterator( Node node )
        throws Exception
    {
        final Node iteratorNode = node.selectSingleNode( "iterator" );
        final String clazz = iteratorNode.valueOf( "@class" );
        iterator = (ScheduleIterator)Class.forName( clazz ).newInstance();
        iterator.init( iteratorNode );
    }

    protected byte [] doZMQ( final com.google.protobuf.Message request, final ZMQ.Socket zmq )
        throws Exception
    {
        if( null == zmq )
        {
            throw new Exception( "No ZMQ connection." );
        }

        zmq.send( request.toByteArray(), 0 );
        final byte [] response = zmq.recv( 0 );
        return response;
    }

    protected String getUTCDay( )
    {
        final SimpleDateFormat sdf = new SimpleDateFormat( "yyyyMMdd" );
        sdf.setTimeZone( TimeZone.getTimeZone( "GMT" ) );

        return sdf.format( new Date() );
    }

    protected String getMarkTime( final String mark_tm )
        throws Exception
    {
        //
        //  Assume that the settlement date is today.
        //

        final SimpleDateFormat sdf = new SimpleDateFormat( "MM'/'dd'/'yy" );

        return getMarkTime( sdf.format( new Date() ), mark_tm );
    }

    protected String getMarkTime( final String settlement_dt, final String mark_tm )
        throws Exception
    {
        final String as_of_tm = settlement_dt + mark_tm;
        final SimpleDateFormat sdf = new SimpleDateFormat( "MM'/'dd'/'yyHHmm z" );

        final SimpleDateFormat mark_df = new SimpleDateFormat( "yyyyMMddHHmm" );
        mark_df.setTimeZone( TimeZone.getTimeZone( "GMT" ) );

        return mark_df.format( sdf.parse( as_of_tm ) ) + "00000000";
    }

    protected Date getRolloverTime( final String rollover_tm )
        throws Exception
    {
        //
        //  Assume that the settlement date is today. We need to convert the
        //  rollover_tm into GMT epoch seconds.
        //

        final SimpleDateFormat sdf = new SimpleDateFormat( "HHmm z" );

        //
        //  Get a Date representing the rollover_tm
        //

        return sdf.parse( rollover_tm );
    }


/*
    protected String doZMQ( final String data, final ZMQ.Socket zmq )
        throws Exception
    {
        if( null == zmq )
        {
            throw new Exception( "No ZMQ connection." );
        }

        //
        //  Calculate the string length
        //

        int str_len = data.length();
        final byte [] str_len_arr =
            new byte[] {
                (byte)(str_len >> 24),
                (byte)(str_len >> 16),
                (byte)(str_len >> 8),
                (byte)(str_len) };

        //
        //  Compress the data
        //

        int count = 0;
        final byte [] buf = new byte[ 1024 ];
        final Deflater compressor = new Deflater();
        compressor.setLevel( Deflater.DEFAULT_COMPRESSION );
        compressor.setInput( data.getBytes() );
        compressor.finish();

        final ByteArrayOutputStream send_bos =
            new ByteArrayOutputStream(
                compressor.getTotalIn() + 12 + 4 );

        //
        //  Write the string length array to the output stream
        //

        send_bos.write( str_len_arr, 0, 4 );

        //
        //  Write the compressed data
        //

        while(! compressor.finished( ) )
        {
            count = compressor.deflate( buf );
            send_bos.write( buf, 0, count );
        }

        send_bos.close();
        zmq.send( send_bos.toByteArray(), 0 );

        //
        //  Get the response
        //

        final byte [] response = zmq.recv( 0 );
        str_len =   (response[ 0 ]) << 24 |
                    (response[ 1 ]&0xff) << 16 |
                    (response[ 2 ]&0xff) << 8 |
                    (response[ 3 ]&0xff);

        final Inflater decompressor = new Inflater();
        decompressor.setInput( response, 4, response.length - 4 );

        final ByteArrayOutputStream recv_bos =
            new ByteArrayOutputStream( str_len );

        while(! decompressor.finished( ) )
        {
            count = decompressor.inflate( buf );
            recv_bos.write( buf, 0, count );
        }

        recv_bos.close();

        return new String( recv_bos.toByteArray() ).trim();
    }
*/
}
