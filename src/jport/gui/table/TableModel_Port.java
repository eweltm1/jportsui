package jport.gui.table;


import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import jport.PortsConstants.EPortMark;
import jport.PortsConstants.EPortStatus;
import jport.TheApplication;
import jport.common.Elemental;
import jport.common.Elemental.EElemental;
import jport.common.Providers_.JPopupMenuProvidable;
import jport.common.Providers_.WidthProvidable;
import jport.common.Util;
import jport.common.gui.AEnumTableModel_Array;
import jport.gui.MarkConfirmUi;
import jport.gui.TheUiHolder;
import jport.type.Portable;


/**
 * The big Port inventory table at +15,000 rows.
 *
 * @author sbaber
 */
@SuppressWarnings("serial")
public class TableModel_Port extends AEnumTableModel_Array<Portable,TableModel_Port.EColumn>
    implements Elemental.Listenable<Portable>
{
    static enum EColumn implements WidthProvidable
            { MARK        (  90 ) // status request
            , _           (  18 ) // status lexigraph
            , NAME        ( 180 ) // port name
            , INSTALLED   ( 120 ) // installed version
            , LATEST      ( 120 ) // latest version
            , DESCRIPTION (  -1 ) // port description
            ;
                    private EColumn( final int width ) { fWidth = width; }
                    private int fWidth;
                    @Override public int provideWidth() { return fWidth; }
            }

    static
    {}

    public TableModel_Port()
    {
        super
            ( Portable.class
            , EEditable.DISABLE
            , ERowSelection.SINGLE_SELECTION
            , EColorize.ALT_BAR_1
            , EColumn.values()
            );

        this.toggelSortColumn( EColumn.NAME );

        // listener
        TheApplication.INSTANCE.getPortsNotifier().addListener( this );

        if( JPopupMenuProvidable.class.isAssignableFrom( Portable.class ) == false )
        {   // future proofing
            getJTable().addMouseListener( new PrivateListener() );
        }
    }

    /**
     * Put Marks at top in Name order.
     */
    public void setTableSortByMark()
    {
        final List<? extends SortKey> list = Arrays.asList
                ( new SortKey( EColumn.MARK.ordinal(), SortOrder.DESCENDING )
                , new SortKey( EColumn.NAME.ordinal(), SortOrder.ASCENDING )
                );
        this.getJTable().getRowSorter().setSortKeys( list );
    }

    public void setTableSortByName()
    {
        final List<? extends SortKey> list = Arrays.asList( new SortKey( EColumn.NAME.ordinal(), SortOrder.ASCENDING ) );
        this.getJTable().getRowSorter().setSortKeys( list );
    }

    @Override public void notify( final EElemental elemental, final Portable obj )
    {
        final int index = Util.indexOf( obj, this.getRowArray() ); // linear search

        switch( elemental )
        {
            case UPDATED : this.fireTableRowsUpdated( index, index ); break;
        }
    }

    /**
     *
     * @param port row
     * @param columnEnum
     * @return placed in table
     */
    @Override public Object getValueOf( final Portable port, final EColumn columnEnum )
    {
        switch( columnEnum )
        {   // 'null' also ok with DefaultCellRenderer
            case MARK        : return _getMark( port );
            case _           : return _html( port ) + _lexigraph( port );
            case NAME        : return port.getName(); // html colors mess up sorting
            case INSTALLED   : return _html( port ) + port.getVersionInstalled();
            case LATEST      : return port.getVersion();
            case DESCRIPTION : return port.getShortDescription();
//            case DESCRIPTION : return "<HTML><SMALL>"+ row.getShortDescription(); no elipses
//            case LICENSE     : return ( row.getLicenses() == Util.EMPTY_STRINGS ) ? "" : Arrays.toString( row.getLicenses() );

            default : return "ERR";
        }
    }

    static private String _getMark( final Portable port )
    {
        if( port.isUnmarked() == true ) return "";

        final EPortMark mark = port.getMark();
        final String string = mark.toString();

        switch( mark )
        {
            case Activate : case Deactivate : 
            case Install : case Uninstall :
            case Upgrade :
                    return "<HTML><B>"+ string;

            case Dependency_Activate : case Dependant_Deactivate :
            case Dependency_Install : case Dependant_Uninstall :
            case Dependency_Upgrade :
                    return string;

            case Conflicted :
                    return "<HTML><I><FONT color=red>"+ string;

            default: return "ERR";
        }
    }

    static private String _html( final Portable port )
    {
        if( port.hasStatus( EPortStatus.UNINSTALLED ) == true )
        {
            return "";
        }
        else
        {   // sequential logic
            return "<HTML><FONT "+ _getHtmlColor( port.provideForeColor() ) +'>'+
                    ( port.hasStatus( EPortStatus.OUTDATED ) ? "<B>"
                    : port.hasStatus( EPortStatus.INACTIVE ) ? "<I>"
                                                             : "" // installed
                    );
        }
    }

    /**
     *
     * @param color strips 31..24 bits alpha component
     * @return
     */
    static private String _getHtmlColor( final Color color )
    {
        return " color=#" + Integer.toHexString( color.getRGB() & 0x00FFFFFF );
    }

    /**
     *
     * @param port
     * @return status as a Character
     */
    static private String _lexigraph( final Portable port )
    {   // sequential logic search
        return port.hasStatus( EPortStatus.UNINSTALLED ) ? ""
             : port.hasStatus( EPortStatus.OUTDATED    ) ? "\u2022" // unicode bullet
             : port.hasStatus( EPortStatus.ACTIVE      ) ? "\u221A" // unicode square root
             : port.hasStatus( EPortStatus.INACTIVE    ) ? "~"
                                                         : "";
    }

    /**
     * Zomg's automatic popup explodes when HoverManager called.
     *
     * @param me
     * @param parentComponent
     * @param port
     */
    static private void doPopupMenu
            ( final MouseEvent me
            , final Component parentComponent
            , final Portable port
            )
    {
        if( port != null )
        {
            final EPortMark portMark = port.getMark();
            final JPopupMenu jpm = new JPopupMenu(); // Mac-PLAF no titled menu param

            final JMenuItem titleItem = new JMenuItem( "<HTML><B>"+ port.getName() +"</B><SMALL> Details" );
            titleItem.setEnabled( true );
            jpm.add( titleItem );
            titleItem.addActionListener( new ActionListener() // anonymous class
                    {   @Override public void actionPerformed( ActionEvent e )
                        {   TheUiHolder.INSTANCE.getCommander().openSelectionDetails();
                        }
                    } ) ;

            jpm.addSeparator(); // ----------

            for( final EPortMark markEnum : Util.reverseOrder( EPortMark.VALUES ) )
            {
                if( markEnum.provideIsVisible() == true && markEnum.isApplicable( port ) == true )
                {   // non-Dep label and applicable to the port
                    final JMenuItem item = new JRadioButtonMenuItem( markEnum.toString() );
                    item.setToolTipText( markEnum.provideTipText() );
                    if( markEnum == portMark )
                    {
                        item.setSelected( true );
                        item.setEnabled( false );
                    }
                    jpm.add( item );

                    // listener
                    item.addActionListener( new ActionListener() // anonymous class
                            {   @Override public void actionPerformed( ActionEvent e )
                                {   MarkConfirmUi.showConfirmation( port, markEnum );
                                }
                            } ) ;
                }
            }

            if( portMark != null )
            {   // show Unmark
                jpm.addSeparator(); // ----------

                final JMenuItem unmarkItem = new JMenuItem( "<HTML>Unmark" );
                jpm.add( unmarkItem );
                unmarkItem.addActionListener( new ActionListener() // anonymous class
                        {   @Override public void actionPerformed( ActionEvent e )
                            {   port.unmark();
                            }
                        } ) ;
            }

            // no awt.GLCanvas so lightweight pane ok
            jpm.show( parentComponent, me.getX(), me.getY() );
        }
    }

    // ================================================================================
    /**
     * Double-click shows Details view and right-click shows mark menu.
     */
    private class PrivateListener extends MouseAdapter
    {
        @Override public void mouseClicked( final MouseEvent e )
        {
            final Object obj = e.getSource();
            final JTable jTable = (JTable)obj;
            if( jTable.isEnabled() == true )
            {
                if( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 )
                {   // double-clicked
                    final Portable selectedPort = TableModel_Port.this.getSingleSelection();
                    if( TheUiHolder.INSTANCE.getCommander().selectPort( selectedPort ) == true )
                    {
                        TheUiHolder.INSTANCE.getCommander().openSelectionDetails();
                    }
                }
                else if( e.isPopupTrigger() == true || e.getButton() == MouseEvent.BUTTON3 ) // || ( e.getModifiers() & InputEvent.BUTTON3_MASK ) == InputEvent.BUTTON3_MASK )
                {   // e.isControlDown() is used to deselect selections
                    // have right-click simulate a selection click
                    final int row = jTable.rowAtPoint( e.getPoint() ); // did test, view->model xlation not needed
                    if( row > -1 ) jTable.setRowSelectionInterval( row, row );

                    final Portable selectedPort = TableModel_Port.this.getSingleSelection();
                    if( TheUiHolder.INSTANCE.getCommander().selectPort( selectedPort ) == true )
                    {
                        doPopupMenu( e, jTable, selectedPort );
                    }
                }
            }
        }
    }
}
