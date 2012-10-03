package oz.zomg.jport;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import oz.zomg.jport.common.Elemental;
import oz.zomg.jport.common.Elemental.EElemental;
import oz.zomg.jport.common.Elemental.Listenable;
import oz.zomg.jport.common.Notification;
import oz.zomg.jport.common.Notification.Notifiable;
import oz.zomg.jport.common.Notification.OneArgumentListenable;
import oz.zomg.jport.common.Reset.Resetable;
import oz.zomg.jport.common.Reset.Reseter;
import oz.zomg.jport.gui.TheUiHolder;
import oz.zomg.jport.ports.PortsCatalog;
import oz.zomg.jport.ports.PortsCliUtil;
import oz.zomg.jport.ports.PortsMarker;
import oz.zomg.jport.type.CliPortInfo;
import oz.zomg.jport.type.EPortStatus;
import oz.zomg.jport.type.Portable;


/**
 * Manages the current PortsCatalog collection, specialized event notification,
 * and mutable status change request information as a singleton.
 *
 * @author <SMALL>Copyright 2012 by Stephen Baber
 * &nbsp; <a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/3.0/deed.en_US">
 * <img alt="Creative Commons License" style="border-width:0" src="http://i.creativecommons.org/l/by-nc-nd/3.0/80x15.png" /></a><br />
 * This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/3.0/deed.en_US">
 * Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License</a>.</SMALL>
 */
public class TheApplication
{
    /** Manages the current PortsCatalog collection and mutable information as a singleton. */
    static final public TheApplication INSTANCE = new TheApplication();

    static
    {}

    /** Notifies when "PortIndex" file has been parsed and Port status queried. */
    final private Reseter fCatalogResetNotifier = new Reseter();

    /** Facility for driving progress bar when ports CLI echo Ports status query is ongoing. */
    final private Notification.Notifier<EPortStatus> fEchoStatusNotifier = new Notification.Notifier<EPortStatus>();

    /** Get notifications of individual  */
    final private Elemental.Notifier<Portable> fPortElementNotifier = new Elemental.Notifier<Portable>( Portable.NONE );

    final private PortsMarker fPortsMarker = new PortsMarker();

    volatile private PortsCatalog vCurrentPortsCatalog = PortsCatalog.NONE;

    /**
     * Singleton constructor.
     * Needs to be fast as many Threads are waiting on TheApplication.INSTANCE being non-null!
     */
    private TheApplication() {}

    /**
     * Constructor needed to be fast as many Threads were waiting on TheApplication.INSTANCE being non-null!
     */
    public void init()
    {
        final Object MONITOR = new Object();

        // fork GUI building into Event Dispatch thread as required by Swing guidelines
        SwingUtilities.invokeLater( new Runnable() // anonymous class
                {   @Override public void run()
                    {   synchronized( MONITOR )
                        {
                            TheOsBinaries.INSTANCE.has( "?" ); // hey lazy Swing thread, do some work!
                            TheUiHolder.INSTANCE.init(); // start Swing in EDT thread
                            MONITOR.notifyAll();
                        }
                    }
                } );

        // if only one Processor Core, wait until GUI finishes constructing
        if( Runtime.getRuntime().availableProcessors() == 1 )
        {
            try
            {
                synchronized( MONITOR )
                {
                    while( TheUiHolder.isReady() == false )
                    {
                        MONITOR.wait( 2000 ); // *BLOCKS*
                    }
                }
            }
            catch( InterruptedException ex )
            {}
        }

        // but start parsing "PortsIndex" in this thread
        TheApplication.INSTANCE.probeUpdate(); // *BLOCKS*

        // enable ports table in EDT even if GUI reported not ready
        SwingUtilities.invokeLater( new Runnable() // anonymous class
                {   @Override public void run()
                    {   TheUiHolder.INSTANCE.goLive();
                    }
                } );
    }

    /**
     *
     * @return facility for letting the user indicate desired port status change requests
     */
    public PortsMarker getPortsMarker() { return fPortsMarker; }

    /**
     *
     * @return instance managing the current contents of the "PortIndex" file
     */
    public PortsCatalog getPortsCatalog() { return vCurrentPortsCatalog; }

    /**
     * Reload catalog index, status, and modification dates.
     * The time stamp of the current catalog is the refresh time.
     * Completely refreshes the GUI in regards to the status of all known Ports.
     * Enables the main table for selections.
     */
    synchronized public void probeUpdate()
    {
        // complete parsing "PortsIndex" in this thread
        vCurrentPortsCatalog = new PortsCatalog( vCurrentPortsCatalog );

        //  since I/O bound, fetch the port folder modification dates from the file system in a seperate thread
        new Thread
                ( new Runnable() // anonymous class
                        {   @Override public void run()
                            {   vCurrentPortsCatalog.scanDates();
                            }
                        }
                , TheApplication.class.getCanonicalName()
                ).start();

        // from the Swing thread, reload port table, clear selection etc.
        if( TheUiHolder.isReady() == true )
        {
            SwingUtilities.invokeLater( new Runnable()
                    {   @Override public void run()
                        {   TheApplication.INSTANCE.causeReset();
                            deprang();
                        }
                    } );
        }
    }

    /**
     * Unmark any applied changes from CLI or JPortsUI.
     * Turn the JTable back on.
     */
    public void deprang()
    {
        SwingUtilities.invokeLater( new Runnable()
                {   @Override public void run()
                    {   getPortsMarker().exchangeAudit( vCurrentPortsCatalog );
                        TheUiHolder.INSTANCE.goLive();
                    }
                } );
    }

    /**
     * Subscribe to Ports Catalog reset notifications.
     *
     * @return just the listener .add() & .remove() aspect of the interface
     */
    public Notifiable<Resetable> getResetNotifier() { return fCatalogResetNotifier; }

    /**
     * Subscribe to Ports Catalog reset notifications.
     *
     * @return just the listener .add() & .remove() aspect of the interface
     */
    public Notifiable<OneArgumentListenable<EPortStatus>> getEchoStatusNotifier() { return fEchoStatusNotifier; }

    /**
     * Subscribe to Port elemental notifications.
     *
     * @return just the listener .add() & .remove() aspect of the interface
     */
    public Notifiable<Listenable<Portable>> getCrudNotifier() { return fPortElementNotifier; }

    /**
     * Sends message to all C.R.U.D. listeners
     *
     * @param elemental
     * @param port
     */
    public void causeCrudNotification( final EElemental elemental, final Portable port )
    {
        fPortElementNotifier.causeNotification( elemental, port );
    }

    public void causeReset()
    {
        fCatalogResetNotifier.causeReset();
    }

    /**
     * Full accounting avoids asking for All ports or Uninstalled ports as
     * these are assumed from the "PortIndex" parsing.
     * Note: Inefficient but I do not know a way to get all status attributes for each installed port, see "man port"
     *
     * @return as reported by the CLI "port echo installed" all of which are type InstalledPort
     */
    public Map<EPortStatus,Set<CliPortInfo>> cliEchoAllStatus()
    {
        final Map<EPortStatus,Set<CliPortInfo>> status_to_InfoSet_Map = new EnumMap<EPortStatus, Set<CliPortInfo>>( EPortStatus.class );

        for( final EPortStatus statusEnum : EPortStatus.VALUES )
        {
            switch( statusEnum )
            {
                case ALL         : // fall-thru
                case UNINSTALLED :
                    {   // do not run CLI on these, too large/slow for a sanity check
                        final Set<CliPortInfo> emptySet = Collections.emptySet();
                        status_to_InfoSet_Map.put( statusEnum, emptySet );
                    }   break;

                default :
                    {   fEchoStatusNotifier.causeNotification( statusEnum );
                        final Set<CliPortInfo> cpiSet = PortsCliUtil.cliEcho( statusEnum );
                        status_to_InfoSet_Map.put( statusEnum, cpiSet );
                        break;
                    }
            }
        }

        return status_to_InfoSet_Map;
    }
}
