package cz.vity.freerapid.gui.dialogs;

import com.jgoodies.binding.adapter.Bindings;
import com.jgoodies.binding.list.SelectionInList;
import com.jgoodies.binding.value.DelayedReadValueModel;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.common.collect.ArrayListModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.*;
import cz.vity.freerapid.core.AppPrefs;
import cz.vity.freerapid.core.FileTypeIconProvider;
import cz.vity.freerapid.core.MainApp;
import cz.vity.freerapid.core.UserProp;
import cz.vity.freerapid.gui.content.ContentPanel;
import cz.vity.freerapid.gui.managers.FileHistoryItem;
import cz.vity.freerapid.gui.managers.FileHistoryManager;
import cz.vity.freerapid.gui.managers.ManagerDirector;
import cz.vity.freerapid.gui.managers.MenuManager;
import cz.vity.freerapid.model.DownloadFile;
import cz.vity.freerapid.plugins.webclient.DownloadState;
import cz.vity.freerapid.swing.SwingUtils;
import cz.vity.freerapid.swing.SwingXUtils;
import cz.vity.freerapid.swing.Swinger;
import cz.vity.freerapid.swing.binding.MyPreferencesAdapter;
import cz.vity.freerapid.utilities.*;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.swinghelper.buttonpanel.JXButtonPanel;
import org.jdesktop.swingx.JXStatusBar;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.table.TableColumnExt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Vity
 */
@SuppressWarnings("UnusedDeclaration")
public class DownloadHistoryDialog extends AppFrame implements ClipboardOwner, ListSelectionListener, PropertyChangeListener {
    private final static Logger logger = Logger.getLogger(DownloadHistoryDialog.class.getName());
    private static final String DATA_ADDED_PROPERTY = "dataAdded";
    private FileHistoryManager manager;
    private static final int COLUMN_DATE = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_DESCRIPTION = 2;
    private static final int COLUMN_SIZE = 3;
    private static final int COLUMN_URL = 4;
    private static final int COLUMN_CONNECTION = 5;
    private static final int COLUMN_AVG_SPEED = 6;

    private static final int BAR_HEIGHT = 18;

    private static final String SELECTED_ACTION_ENABLED_PROPERTY = "selectedEnabled";
    private boolean selectedEnabled;
    private static final String FILE_EXISTS_ENABLED_PROPERTY = "fileExistsEnabled";
    private boolean fileExistsEnabled;
    private final ManagerDirector director;

    private final String exampleSearchString;

    public DownloadHistoryDialog(Frame owner, ManagerDirector director) throws HeadlessException {
        super(owner);
        this.director = director;
        this.manager = director.getFileHistoryManager();
        this.setName("DownloadHistoryDialog");
        this.exampleSearchString = getResourceMap().getString("exampleSearchString");
        try {
            initComponents();
            build();
        } catch (Exception e) {
            LogUtils.processException(logger, e);
            doClose();
        }
    }


    @Override
    protected AbstractButton getBtnOK() {
        return okButton;
    }

    @Override
    protected AbstractButton getBtnCancel() {
        return okButton;
    }

    private void build() {
        inject();
        buildGUI();

        //final ActionMap actionMap = getActionMap();
        setAction(okButton, "okBtnAction");
        setAction(clearHistoryBtn, "clearHistoryBtnAction");

        registerKeyboardAction("downloadInformationAction");
        registerKeyboardAction("openFileAction");
        registerKeyboardAction("deleteFileAction");
        registerKeyboardAction("openDirectoryAction");
        registerKeyboardAction("openInBrowser");
        registerKeyboardAction("removeSelectedAction");
        registerKeyboardAction("copyContent");
        registerKeyboardAction("copyURL");

        updateActions();

        manager.addPropertyChangeListener("dataAdded", this);

        pack();
        locateOnOpticalScreenCenter(this);
    }


    private void initTable() {
        table.setName("historyTable");
        table.setModel(new CustomTableModel(new ArrayListModel<FileHistoryItem>(manager.getItems()), getList("columns", 7)));
        table.setAutoCreateColumnsFromModel(false);
        table.setEditable(false);
        table.setColumnControlVisible(true);
        table.setSortable(true);
        table.setSortOrderCycle(SortOrder.ASCENDING, SortOrder.DESCENDING, SortOrder.UNSORTED);
        table.setColumnMargin(10);
        table.setRolloverEnabled(true);

        if (!AppPrefs.getProperty(UserProp.SLIM_LINES_IN_HISTORY, UserProp.SLIM_LINES_IN_HISTORY_DEFAULT)) {
            table.setRowHeight(36);
        }
        table.setShowGrid(true, false);

        table.setColumnSelectionAllowed(false);

        table.getSelectionModel().addListSelectionListener(this);
        table.getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        updateInfoStatus();
                    }
                });
            }
        });

        table.createDefaultColumnsFromModel();
        Swinger.updateColumn(table, "Date", COLUMN_DATE, -1, 40, new DateCellRenderer(getResourceMap()));
        Swinger.updateColumn(table, "Name", COLUMN_NAME, -1, 150, new FileNameCellRenderer(director.getFileTypeIconProvider()));
        Swinger.updateColumn(table, "Description", COLUMN_DESCRIPTION, -1, 170, new DescriptionCellRenderer());
        final TableColumnExt size = (TableColumnExt) Swinger.updateColumn(table, "Size", COLUMN_SIZE, -1, 40, new SizeCellRenderer());
        Swinger.updateColumn(table, "URL", COLUMN_URL, -1, -1, SwingXUtils.getHyperLinkTableCellRenderer());
        final TableColumnExt connection = (TableColumnExt) Swinger.updateColumn(table, "Connection", COLUMN_CONNECTION, -1, -1, new ConnectionCellRenderer());
        final TableColumnExt avgSpeed = (TableColumnExt) Swinger.updateColumn(table, "AvgSpeed", COLUMN_AVG_SPEED, -1, -1, new AvgSpeedCellRenderer());
        size.setComparator(new SizeColumnComparator());
        avgSpeed.setComparator(new AvgSpeedColumnComparator());
        avgSpeed.setVisible(false);
        connection.setVisible(false);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!table.hasFocus())
                    Swinger.inputFocus(table);
                if (SwingUtilities.isRightMouseButton(e))
                    showPopMenu(e);
                else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    openFileAction();
                }
            }
        });

        final InputMap tableInputMap = table.getInputMap();
        final ActionMap tableActionMap = table.getActionMap();
        final ActionMap actionMap = getActionMap();

        tableInputMap.put(SwingUtils.getCtrlKeyStroke(KeyEvent.VK_C), "copy");
        tableActionMap.put("copy", actionMap.get("copyContent"));

        tableInputMap.put(SwingUtils.getShiftKeyStroke(KeyEvent.VK_DELETE), "deleteFileAction");
        tableActionMap.put("deleteFileAction", actionMap.get("deleteFileAction"));

        tableInputMap.put(SwingUtils.getCtrlKeyStroke(KeyEvent.VK_ENTER), "openDirectoryAction");
        tableActionMap.put("openDirectoryAction", actionMap.get("openDirectoryAction"));

        final KeyStroke ctrlF = SwingUtils.getCtrlKeyStroke(KeyEvent.VK_F);
        tableInputMap.put(ctrlF, "getFocusFind");
        final AbstractAction focusFilterAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Swinger.inputFocus(fieldFilter);
            }
        };
        tableActionMap.put("getFocusFind", focusFilterAction);

        table.getParent().setPreferredSize(new Dimension(600, 400));

        tableInputMap.put(SwingUtils.getShiftKeyStroke(KeyEvent.VK_HOME), "selectFirstRowExtendSelection");
        tableInputMap.put(SwingUtils.getShiftKeyStroke(KeyEvent.VK_END), "selectLastRowExtendSelection");

        registerKeyboardAction(focusFilterAction, ctrlF);
//        this.getRootPane().getInputMap().put(ctrlF, "getFocusFind");
//        this.getRootPane().getActionMap().put("getFocusFind", focusFilterAction);
    }

    @org.jdesktop.application.Action(enabledProperty = SELECTED_ACTION_ENABLED_PROPERTY)
    public void copyContent() {
        final int[] rows = getSelectedRows();

        final TableModel tableModel = table.getModel();

        final int selCol = table.convertColumnIndexToModel(table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        StringBuilder builder = new StringBuilder();
        String value;
        for (int row : rows) {
            if (selCol == COLUMN_DATE) {
                final Calendar instance = Calendar.getInstance();
                instance.setTimeInMillis((Long) tableModel.getValueAt(row, selCol));
                value = String.format("%1$tm %1$tB,%1$tY", instance);
            } else {
                value = tableModel.getValueAt(row, selCol).toString();
            }
            builder.append(value.replaceAll("%23", "#")).append('\n');
        }
        SwingUtils.copyToClipboard(builder.toString().trim(), this);
    }


    @Action
    public void copyURL() {
        final java.util.List<FileHistoryItem> files = getSelectionToList(getSelectedRows());
        StringBuilder builder = new StringBuilder();
        for (FileHistoryItem file : files) {
            builder.append(file.getUrl().toExternalForm()).append('\n');
        }
        SwingUtils.copyToClipboard(builder.toString().trim(), this);
    }

    @org.jdesktop.application.Action(enabledProperty = SELECTED_ACTION_ENABLED_PROPERTY)
    public void downloadInformationAction() throws Exception {
        final int[] indexes = getSelectedRows();
        final java.util.List<FileHistoryItem> filesH = getSelectionToList(indexes);
        if (filesH.isEmpty())
            return;
        final java.util.List<DownloadFile> files = new ArrayList<DownloadFile>();
        for (FileHistoryItem fileH : filesH) {
            final DownloadFile down = new DownloadFile();
            down.setFileUrl(fileH.getUrl());
            down.setStoreFile(fileH.getOutputFile());
            down.setSaveToDirectory((fileH.getOutputFile() != null) ? new File(fileH.getOutputFile().getAbsolutePath()) : null);
            down.setDescription(fileH.getDescription());
            down.setFileName(fileH.getFileName());
            down.setFileSize(fileH.getFileSize());
            down.setDownloaded(fileH.getFileSize());
            down.setRealDownload(fileH.getFileSize());
            down.setFileType(fileH.getFileType());
            down.setAverageSpeed(fileH.getAverageSpeed());
            down.setPluginID(fileH.getShareDownloadServiceID());
            down.setState(DownloadState.COMPLETED);
            files.add(down);
        }
        if (files.size() == 1) {
            final InformationDialog dialog = new InformationDialog(owner, director, files.get(0));
            ((MainApp) director.getContext().getApplication()).show(dialog);
        } else {
            final MultipleSettingsDialog dialog = new MultipleSettingsDialog(owner, files);
            ((MainApp) director.getContext().getApplication()).show(dialog);
        }
    }

    @org.jdesktop.application.Action(enabledProperty = FILE_EXISTS_ENABLED_PROPERTY)
    public void openFileAction() {
        final int[] indexes = getSelectedRows();
        final java.util.List<FileHistoryItem> files = getSelectionToList(indexes);
        for (FileHistoryItem file : files) {
            File outputFile = file.getOutputFile();
            if ((outputFile != null) && outputFile.exists()) {
                OSDesktop.openFile(outputFile);
            }
        }
    }

    @org.jdesktop.application.Action(enabledProperty = DownloadHistoryDialog.SELECTED_ACTION_ENABLED_PROPERTY)
    public void openInBrowser() {
        final java.util.List<FileHistoryItem> files = getSelectionToList(getSelectedRows());
        for (FileHistoryItem file : files) {
            Browser.openBrowser(file.getUrl().toExternalForm().replaceAll("%23", "#"));
        }
    }

    @org.jdesktop.application.Action(enabledProperty = SELECTED_ACTION_ENABLED_PROPERTY)
    public void openDirectoryAction() {
        final int[] indexes = getSelectedRows();
        final java.util.List<FileHistoryItem> files = getSelectionToList(indexes);
        for (FileHistoryItem file : files) {
            File outputFile = file.getOutputFile();
            if ((outputFile != null) && outputFile.exists()) {
                OSDesktop.openDirectoryForFile(outputFile);
            }
        }
    }

    @Action
    public void cancelBtnAction() {
        doClose();
    }

    @Override
    public void doClose() {
        manager.removePropertyChangeListener(DATA_ADDED_PROPERTY, this);
        if (AppPrefs.getProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, exampleSearchString).equals(exampleSearchString))
            AppPrefs.storeProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, "");
        super.doClose();
    }

    private void buildGUI() {
        initTable();

        if ("Search...".equals(AppPrefs.getProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, "")))//hack for 0.6 and older
            AppPrefs.storeProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, "");

        if ("".equals(AppPrefs.getProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, "")))
            AppPrefs.storeProperty(UserProp.CONTAIN_DOWNLOADS_FILTER, exampleSearchString);

        final MyPreferencesAdapter adapter = new MyPreferencesAdapter(UserProp.CONTAIN_DOWNLOADS_FILTER, "");
        final DelayedReadValueModel delayedReadValueModel = new DelayedReadValueModel(adapter, 300, true);
        delayedReadValueModel.addValueChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                updateFilters();
            }
        });


        fieldFilter.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                if (exampleSearchString.equals(fieldFilter.getText())) {
                    fieldFilter.setForeground(Color.BLACK);
                    fieldFilter.setText("");
                } else fieldFilter.selectAll();
            }

            public void focusLost(FocusEvent e) {
                if (fieldFilter.getText().isEmpty()) {
                    fieldFilter.setForeground(Color.GRAY);
                    fieldFilter.setText(exampleSearchString);
                }
            }
        });

        fieldFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                final int keyCode = e.getKeyCode();
                if (KeyEvent.VK_ESCAPE == keyCode) {
                    if (!"".equals(fieldFilter.getText())) {
                        fieldFilter.setText("");
                        e.consume();
                    }
                } else if (KeyEvent.VK_ENTER == keyCode || KeyEvent.VK_DOWN == keyCode) {
                    if (getSelectedRows().length == 0) {
                        if (table.getRowCount() > 0)
                            table.setRowSelectionInterval(0, 0);
                    }
                    Swinger.inputFocus(table);
                    e.consume();
                }
            }
        });

        Bindings.bind(fieldFilter, delayedReadValueModel);
        //combobox.setModel(new DefaultComboBoxModel(getList("datesFilter")));

        bindCheckbox(checkbox, UserProp.CHECK_RECENT_DOWNLOAD_HISTORY, UserProp.CHECK_RECENT_DOWNLOAD_HISTORY_DEFAULT);
        bindCombobox(combobox, UserProp.SELECTED_DOWNLOADS_FILTER, DownloadsFilters.ALL_DOWNLOADS.ordinal(), "datesFilter", 8);

        checkbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateFilters();
            }
        });
        combobox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateFilters();
            }
        });

        if (!exampleSearchString.equals(fieldFilter.getText())) {
            fieldFilter.setForeground(Color.BLACK);
        } else {
            fieldFilter.setForeground(Color.GRAY);
        }


        updateFilters();

        Swinger.inputFocus(table);
    }

    @Action
    public void okBtnAction() {
        doClose();
    }

    private int[] getSelectedRows() {
        return Swinger.getSelectedRows(table);
    }

    private void bindCheckbox(final JCheckBox checkbox, final String key, final Object defaultValue) {
        final MyPreferencesAdapter checkAdapter = new MyPreferencesAdapter(key, defaultValue);
        Bindings.bind(checkbox, checkAdapter);
    }

    private void bindCombobox(final JComboBox combobox, final String key, final Object defaultValue, final String resourceKey, final int valueCount) {
        final String[] stringList = getList(resourceKey, valueCount);
        bindCombobox(combobox, key, defaultValue, stringList);
    }

    private void bindCombobox(final JComboBox combobox, String key, final Object defaultValue, final String[] values) {
        if (values == null)
            throw new IllegalArgumentException("List of combobox values cannot be null!!");
        final MyPreferencesAdapter adapter = new MyPreferencesAdapter(key, defaultValue);
        final SelectionInList<String> inList = new SelectionInList<String>(values, new ValueHolder(values[(Integer) adapter.getValue()]), adapter);
        Bindings.bind(combobox, inList);
    }


    @SuppressWarnings({"deprecation"})
    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner Open Source Project license - unknown
        //ResourceBundle bundle = ResourceBundle.getBundle("DownloadHistoryDialog");
        JPanel dialogPane = new JPanel();
        JPanel contentPanel = new JPanel();
        JPanel panel1 = new JPanel();
        checkbox = new JCheckBox();
        checkbox.setName("recentHistory");
        combobox = new JComboBox();
        JLabel labelFilter = new JLabel();
        fieldFilter = new JTextField();
        fileCount = new JLabel();
        fileCount.setPreferredSize(new Dimension(120, BAR_HEIGHT));
        totalDownloads = new JLabel();
        totalDownloads.setPreferredSize(new Dimension(100, BAR_HEIGHT));
        JScrollPane scrollPane2 = new JScrollPane();
        table = new JXTable();
        JXButtonPanel buttonBar = new JXButtonPanel();
        clearHistoryBtn = new JButton();
        okButton = new JButton();
        CellConstraints cc = new CellConstraints();

        JXStatusBar statusBar = new JXStatusBar();
        statusBar.add(fileCount, JXStatusBar.Constraint.ResizeBehavior.FIXED);
        statusBar.add(totalDownloads, JXStatusBar.Constraint.ResizeBehavior.FIXED);


        //======== this ========
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.DIALOG);
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {

                //======== panel1 ========
                {
                    panel1.setBorder(new TitledBorder(""));

                    //---- labelFilter ----
                    labelFilter.setName("labelFilter");
                    labelFilter.setLabelFor(fieldFilter);

                    PanelBuilder panel1Builder = new PanelBuilder(new FormLayout(
                            new ColumnSpec[]{
                                    FormSpecs.DEFAULT_COLSPEC,
                                    FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                    ColumnSpec.decode("max(pref;80dlu)"),
                                    FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                    FormSpecs.DEFAULT_COLSPEC,
                                    FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                    new ColumnSpec(Sizes.dluX(100)),
                                    FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                    new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                                    FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                    new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                            },
                            RowSpec.decodeSpecs("default")), panel1);

                    panel1Builder.add(checkbox, cc.xy(1, 1));
                    panel1Builder.add(combobox, cc.xy(3, 1));
                    panel1Builder.add(labelFilter, cc.xy(5, 1));
                    panel1Builder.add(fieldFilter, cc.xy(7, 1));
                }

                //======== scrollPane2 ========
                {
                    scrollPane2.setViewportView(table);
                }

                PanelBuilder contentPanelBuilder = new PanelBuilder(new FormLayout(
                        ColumnSpec.decodeSpecs("default:grow"),
                        new RowSpec[]{
                                FormSpecs.DEFAULT_ROWSPEC,
                                FormSpecs.LINE_GAP_ROWSPEC,
                                new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        }), contentPanel);

                contentPanelBuilder.add(panel1, cc.xy(1, 1));
                contentPanelBuilder.add(scrollPane2, cc.xy(1, 3));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));

                //---- clearHistoryBtn ----
                clearHistoryBtn.setName("clearHistoryBtn");

                //---- okButton ----
                okButton.setName("okButton");

                PanelBuilder buttonBarBuilder = new PanelBuilder(new FormLayout(
                        new ColumnSpec[]{
                                FormSpecs.DEFAULT_COLSPEC,
                                FormSpecs.LABEL_COMPONENT_GAP_COLSPEC,
                                new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                                FormSpecs.UNRELATED_GAP_COLSPEC,
                                ColumnSpec.decode("max(pref;55dlu)")
                        },
                        RowSpec.decodeSpecs("fill:pref")), buttonBar);

                buttonBarBuilder.add(clearHistoryBtn, cc.xy(1, 1));
                buttonBarBuilder.add(okButton, cc.xy(5, 1));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
    }

    public void lostOwnership(Clipboard clipboard, Transferable contents) {

    }

    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting())
            return;
        updateActions();
    }

    private void updateActions() {
        final int[] indexes = getSelectedRows();
        setSelectedEnabled(indexes.length > 0);
        boolean valid = true;
        final java.util.List<FileHistoryItem> items = getSelectionToList(indexes);
        for (FileHistoryItem item : items) {
            File outputFile = item.getOutputFile();
            if (outputFile == null || !outputFile.exists()) {
                valid = false;
                break;
            }
        }
        setFileExistsEnabled(valid);
    }

    public boolean isSelectedEnabled() {
        return this.selectedEnabled;
    }

    public void setSelectedEnabled(final boolean selectedEnabled) {
        boolean oldValue = this.selectedEnabled;
        this.selectedEnabled = selectedEnabled;
        firePropertyChange(SELECTED_ACTION_ENABLED_PROPERTY, oldValue, selectedEnabled);
    }

    public boolean isFileExistsEnabled() {
        return fileExistsEnabled;
    }

    public void setFileExistsEnabled(boolean fileExistsEnabled) {
        boolean oldValue = this.fileExistsEnabled;
        this.fileExistsEnabled = fileExistsEnabled;
        firePropertyChange(FILE_EXISTS_ENABLED_PROPERTY, oldValue, fileExistsEnabled);
    }

    @org.jdesktop.application.Action(enabledProperty = SELECTED_ACTION_ENABLED_PROPERTY)
    public void deleteFileAction() {
        final int[] indexes = getSelectedRows();
        final java.util.List<FileHistoryItem> files = getSelectionToList(indexes);
        final String s = getFileList(files);
        final int result;
        final boolean confirm = AppPrefs.getProperty(UserProp.CONFIRM_FILE_DELETE, UserProp.CONFIRM_FILE_DELETE_DEFAULT);

        final boolean showedDialog;
        if (s.isEmpty() || (!confirm)) {
            showedDialog = false;
            result = Swinger.RESULT_OK;
        } else {
            showedDialog = true;
            result = Swinger.getChoiceOKCancel("message.areyousuredelete", s);
        }
        if (result == Swinger.RESULT_OK) {
            for (FileHistoryItem file : files) {
                final File outputFile = file.getOutputFile();
                if (outputFile != null) {
                    FileUtils.deleteFileWithRecycleBin(outputFile);
                }
            }
            this.removeSelected(indexes, showedDialog);
            selectFirstIfNoSelection();
        }
    }

    private String getFileList(final java.util.List<FileHistoryItem> files) {
        final java.util.List<FileHistoryItem> existingFiles = new ArrayList<FileHistoryItem>();
        for (FileHistoryItem file : files) {
            if (file.getOutputFile() != null && file.getOutputFile().exists()) {
                existingFiles.add(file);
            }
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0, n = Math.min(existingFiles.size(), 20); i < n; i++) {
            builder.append('\n').append(Utils.shortenFileName(existingFiles.get(i).getOutputFile()));
        }
        if (existingFiles.size() > 20) {
            builder.append('\n').append(getResourceMap().getString("andOtherFiles", existingFiles.size() - 20));
        }
        return builder.toString();
    }

    private void removeSelected(final int[] indexes, final boolean quiet) {
        if (!quiet) {
            final boolean confirmRemove = AppPrefs.getProperty(UserProp.CONFIRM_REMOVE, UserProp.CONFIRM_REMOVE_DEFAULT);

            if (confirmRemove) {
                final int result = Swinger.getChoiceOKCancel("areYouSureYouWantToRemove");
                if (result != Swinger.RESULT_OK)
                    return;
            }
        }


        final ListSelectionModel selectionModel = table.getSelectionModel();
        selectionModel.setValueIsAdjusting(true);
        removeSelected(indexes);
        selectionModel.setValueIsAdjusting(false);
        final int min = getArrayMin(indexes);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final int count = table.getRowCount();
                if (table.getRowCount() > 0) {
                    int index = Math.min(count - 1, min);
                    index = table.convertRowIndexToView(index);
                    selectionModel.addSelectionInterval(index, index);
                    scrollToVisible(true);
                }
            }
        });
        updateInfoStatus();
    }


    public java.util.List<FileHistoryItem> getSelectionToList(int[] selectedRows) {
        return selectionToList(selectedRows);
    }

    private java.util.List<FileHistoryItem> selectionToList(int[] indexes) {
        java.util.List<FileHistoryItem> list = new ArrayList<FileHistoryItem>();
        final ArrayListModel<FileHistoryItem> items = getItems();
        for (int index : indexes) {
            list.add(items.get(index));
        }
        return list;
    }

    private ArrayListModel<FileHistoryItem> getItems() {
        return ((CustomTableModel) table.getModel()).model;
    }

    public void removeSelected(int[] indexes) {
        final ArrayListModel<FileHistoryItem> items = getItems();
        final java.util.List<FileHistoryItem> toRemoveList = getSelectionToList(indexes);
        manager.removeItems(toRemoveList);
        items.removeAll(toRemoveList);
    }


    private void scrollToVisible(final boolean up) {
        final int[] rows = table.getSelectedRows();
        final int length = rows.length;
        if (length > 0)
            table.scrollRowToVisible((up) ? rows[0] : rows[length - 1]);
    }


    @org.jdesktop.application.Action(enabledProperty = SELECTED_ACTION_ENABLED_PROPERTY)
    public void removeSelectedAction() {
        final int[] indexes = getSelectedRows();
        this.removeSelected(indexes, false);
    }

    private void selectFirstIfNoSelection() {
        final int[] rows = getSelectedRows();
        if (rows.length == 0) {
            if (getVisibleRowCount() > 0)
                table.getSelectionModel().setSelectionInterval(0, 0);
        }
    }


    private int getVisibleRowCount() {
        return table.getRowSorter().getViewRowCount();
    }

    private int getArrayMin(int[] indexes) {
        int min = Integer.MAX_VALUE;
        for (int i : indexes) {
            if (min > i) {
                min = i;
            }
        }
        return min;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        final CustomTableModel model = (CustomTableModel) table.getModel();
        model.model.add((FileHistoryItem) evt.getNewValue());
    }


    private static class CustomTableModel extends AbstractTableModel implements ListDataListener {
        private final ArrayListModel<FileHistoryItem> model;
        private final String[] columns;


        public CustomTableModel(ArrayListModel<FileHistoryItem> model, String[] columns) {
            super();
            this.model = model;
            this.columns = columns;
            model.addListDataListener(this);
        }

        public int getRowCount() {
            return model.getSize();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public String getColumnName(int column) {
            return this.columns[column];
        }

        public int getColumnCount() {
            return this.columns.length;
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            final FileHistoryItem fileHistoryItem = model.get(rowIndex);
            switch (columnIndex) {
                case COLUMN_DATE:
                    return fileHistoryItem.getFinishedTime();
                case COLUMN_NAME:
                    return fileHistoryItem.getFileName();
                case COLUMN_DESCRIPTION:
                    return fileHistoryItem.getDescription();
                case COLUMN_SIZE:
                    return fileHistoryItem.getFileSize();
                case COLUMN_URL:
                    return SwingXUtils.createLink(fileHistoryItem.getUrl());
                case -1:
                    return fileHistoryItem;
                default:
                    assert false;
            }
            return fileHistoryItem;
        }

        public void intervalAdded(ListDataEvent e) {
            fireTableRowsInserted(e.getIndex0(), e.getIndex1());
        }

        public void intervalRemoved(ListDataEvent e) {
            fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
        }

        public void contentsChanged(ListDataEvent e) {
            fireTableRowsUpdated(e.getIndex0(), e.getIndex1());
        }
    }

    private void showPopMenu(MouseEvent e) {
        int[] selectedRows = getSelectedRows();//vraci model
        ListSelectionModel selectionModel = table.getSelectionModel();
        int rowNumber = table.rowAtPoint(e.getPoint());//vraci view
        if (rowNumber != -1) {
            if (selectedRows.length <= 0) {
                if (getVisibleRowCount() > 0) {
                    selectionModel.setSelectionInterval(rowNumber, rowNumber);//chce view
                }
            } else {
                Arrays.sort(selectedRows);
                if (Arrays.binarySearch(selectedRows, table.convertRowIndexToModel(rowNumber)) < 0) {
                    selectionModel.setValueIsAdjusting(true);
                    table.clearSelection();
                    selectionModel.setSelectionInterval(rowNumber, rowNumber);//chce view
                    selectionModel.setValueIsAdjusting(false);
                }
            }
        } else table.clearSelection();
//        selectedRows = getSelectedRows();//znovu


        final MenuManager menuManager = director.getMenuManager();
        final JPopupMenu popup = new JPopupMenu();
        final Object[] objects = {"downloadInformationAction", MenuManager.MENU_SEPARATOR, "openFileAction", "deleteFileAction", "openDirectoryAction", MenuManager.MENU_SEPARATOR, "copyContent", MenuManager.MENU_SEPARATOR, "copyURL", "openInBrowser", MenuManager.MENU_SEPARATOR, "removeSelectedAction"};
        menuManager.processMenu(popup, "popup", getActionMap(), objects);
        SwingUtils.showPopMenu(popup, e, table, this);
    }

    @org.jdesktop.application.Action
    public void clearHistoryBtnAction() {
        if (Swinger.getChoiceYesNo(getApp().getContext().getResourceMap().getString("confirmClearHistory")) == Swinger.RESULT_YES) {
            final ListSelectionModel selectionModel = table.getSelectionModel();
            manager.clearHistory(new Runnable() {
                @Override
                public void run() {
                    selectionModel.setValueIsAdjusting(true);
                    getItems().clear();
                    selectionModel.setValueIsAdjusting(false);
                }
            });
        }
    }


    @SuppressWarnings({"unchecked"})
    private void updateFilters() {
        String filterText = fieldFilter.getText();
        if (exampleSearchString.equals(filterText))
            filterText = "";
        final int selectedIndex = combobox.getSelectedIndex();
        final boolean recentHistory = checkbox.isSelected();

        final DownloadsFilters filter;
        RowFilter<Object, Object> rowFilter = null;
        if (selectedIndex == -1)
            filter = DownloadsFilters.ALL_DOWNLOADS;
        else {
            filter = DownloadsFilters.values()[selectedIndex];
        }
        checkbox.setEnabled(filter != DownloadsFilters.ALL_DOWNLOADS);

        if (filter != DownloadsFilters.ALL_DOWNLOADS) {
            rowFilter = new DateTimeFilter(filter, recentHistory);
        }

        if (!filterText.isEmpty()) {
            final java.util.List<RowFilter<Object, Object>> textFilters = new ArrayList<RowFilter<Object, Object>>();
            String[] texts = filterText.split(" ");
            for (String text : texts) {
                textFilters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
            }
            if (rowFilter != null) {
                final java.util.List<RowFilter<Object, Object>> list = new ArrayList<RowFilter<Object, Object>>(1 + textFilters.size());
                list.add(rowFilter);
                list.addAll(textFilters);
                rowFilter = RowFilter.andFilter(list);
            } else {
                rowFilter = RowFilter.andFilter(textFilters);
            }
        }
        ((DefaultRowSorter) table.getRowSorter()).setRowFilter(rowFilter);
        updateInfoStatus();
    }

    private void updateInfoStatus() {
        fileCount.setText(table.getRowCount() + " " + getResourceMap().getString("textDownloads"));
        long totalDownloads = 0;
        for (int i = 0; i < table.getRowCount(); i++) {
            totalDownloads += getItems().get(table.convertRowIndexToModel(i)).getFileSize();
        }
        this.totalDownloads.setText(ContentPanel.bytesToAnother(totalDownloads));
    }

//    /**
//     * Filtr pro redukci seznamu podle inputu. Prohledava pres vsechny sloupce (defaultne se prohledava jen pevne
//     * urceny).
//     */
//    private static class AllPatternFilter extends PatternFilter {
//        final boolean filter;
//
//        public AllPatternFilter(String string, int patternFlags, int columnCount) {
//            super(Pattern.quote(string), patternFlags, columnCount);
//            filter = (string != null && !string.isEmpty());
//        }
//
//        @Override
//        public boolean test(int row) {
//            if (!filter)
//                return true;
//            final int maxColumnIndex = getColumnIndex();
//            for (int i = 0; i < maxColumnIndex; ++i) {
//                if (adapter.isTestable(i)) {
//                    Object value = getInputValue(row, i);
//                    if (value != null) {
//                        boolean matches = pattern.matcher(value.toString()).find();
//                        if (matches)
//                            return true;
//                    }
//                }
//            }
//            return false;
//        }
//    }


    private static class DateTimeFilter extends RowFilter<Object, Object> {
        private final DownloadsFilters filter;
        private boolean recent;

        private DateTimeFilter(DownloadsFilters filter, boolean recent) {
            this.filter = filter;
            this.recent = recent;
        }

        @Override
        public boolean include(Entry entry) {
            Long value = (Long) entry.getValue(COLUMN_DATE);

            if (value == null)
                return false;

            if (filter == DownloadsFilters.ALL_DOWNLOADS)
                return true;


            final Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 1);

            final Calendar valueDate = Calendar.getInstance();
            valueDate.setTimeInMillis(value);

            switch (filter) {
                case TODAY:
                    if (valueDate.after(today))
                        return recent;
                    break;
                case YESTERDAY:
                    final Calendar yesterday = Calendar.getInstance();
                    yesterday.setTime(today.getTime());
                    yesterday.add(Calendar.DATE, -1);

                    if (valueDate.after(yesterday) && valueDate.before(today))
                        return recent;
                    break;
                case LAST_WEEK:
                    final Calendar lastWeek = Calendar.getInstance();
                    lastWeek.setTime(today.getTime());
                    lastWeek.add(Calendar.DATE, -7);

                    if (valueDate.after(lastWeek))
                        return recent;
                    break;
                case LAST_MONTH:
                    final Calendar lastMonth = Calendar.getInstance();
                    lastMonth.setTime(today.getTime());
                    lastMonth.add(Calendar.MONTH, -1);

                    if (valueDate.after(lastMonth))
                        return recent;
                    break;
                case THREE_MONTHS:
                    final Calendar last3Months = Calendar.getInstance();
                    last3Months.setTime(today.getTime());
                    last3Months.add(Calendar.MONTH, -3);

                    if (valueDate.after(last3Months))
                        return recent;
                    break;
                case SIX_MONTHS:
                    final Calendar last6Months = Calendar.getInstance();
                    last6Months.setTime(today.getTime());
                    last6Months.add(Calendar.MONTH, -6);

                    if (valueDate.after(last6Months))
                        return recent;
                    break;
                case LAST_YEAR:
                    final Calendar lastYear = Calendar.getInstance();
                    lastYear.setTime(today.getTime());
                    lastYear.add(Calendar.YEAR, -1);

                    if (valueDate.after(lastYear))
                        return recent;
                    break;
                default:
                    assert false;
            }

            return !recent;
        }
    }


    private static class DateCellRenderer extends DefaultTableCellRenderer {
        private final String yesterday;
        private final String dateFormat;

        private DateCellRenderer(ResourceMap map) {
            this.yesterday = map.getString("yesterday");
            this.dateFormat = AppPrefs.getProperty(UserProp.HISTORY_TABLE_DATE_FORMAT, "%1$tB %1$te");
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null)
                value = table.getValueAt(row, column);
            Calendar valueDate = Calendar.getInstance();
            valueDate.setTimeInMillis((Long) value);
            Long time = valueDate.getTime().getTime();
            value = millisToString((Long) value);
            setToolTipText(String.format(dateFormat + " %tH:%tM", time, time));
            this.setHorizontalAlignment(CENTER);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

        private String millisToString(long value) {
            final Calendar valueDate = Calendar.getInstance();
            valueDate.setTimeInMillis(value);
            final Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 1);
            //long todayStart = today.getTimeInMillis();
            if (valueDate.after(today)) {
                return String.format("%tH:%tM", value, value);
            }
            today.add(Calendar.DATE, -1);
            //  System.out.printf("today = %1$tm %1$te,%1$tY %1$tH:%1$tM", today);
            if (valueDate.after(today)) {
                return yesterday;
            }
            today.add(Calendar.DATE, -6);
            if (valueDate.after(today)) {
                return String.format("%tA", value);
            }
            //jinak
            return String.format(dateFormat, value);
        }
    }

    private static class FileNameCellRenderer extends DefaultTableCellRenderer {

        private final FileTypeIconProvider iconProvider;
        private final boolean bigIcon;

        public FileNameCellRenderer(FileTypeIconProvider iconProvider) {
            this.iconProvider = iconProvider;
            this.bigIcon = !AppPrefs.getProperty(UserProp.SLIM_LINES_IN_HISTORY, UserProp.SLIM_LINES_IN_HISTORY_DEFAULT);
        }


        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final FileHistoryItem fileHistoryItem = (FileHistoryItem) table.getValueAt(row, -1);
            final String fn = (String) value;
            final String url = fileHistoryItem.getUrl().toExternalForm();
            if (fn != null && !fn.isEmpty()) {
                value = String.format("<html><b>%s</b></html>", fn);
            } else {
                value = url;
            }

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                this.setToolTipText(url);
                this.setIconTextGap(6);
                this.setIcon(iconProvider.getIconImageByFileType(fileHistoryItem.getFileType(), bigIcon));

            }
            return this;
        }
    }

    private static class DescriptionCellRenderer extends DefaultTableCellRenderer {

        private String tooltip;

        private DescriptionCellRenderer() {
            tooltip = Swinger.getResourceMap().getString("tooltip");
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null)
                value = table.getValueAt(row, column);
            if (value != null) {
                if (!((String) value).isEmpty()) {
                    this.setToolTipText(String.format(tooltip, value));
                    value = "<html>" + value + "</html>";
                } else this.setToolTipText(null);
            } else {
                this.setToolTipText(null);
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static class SizeCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final FileHistoryItem item = (FileHistoryItem) table.getValueAt(row, -1);
            final long fs = item.getFileSize();

            if (fs == -1) {
                value = "";
                this.setToolTipText(null);
            } else {

                value = ContentPanel.bytesToAnother(fs);
                this.setToolTipText(NumberFormat.getIntegerInstance().format(fs) + " B");
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static class ConnectionCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final FileHistoryItem item = (FileHistoryItem) table.getValueAt(row, -1);
            final String connection = item.getConnection();

            if (connection == null) {
                value = "";
                this.setToolTipText(null);
            } else {
                value = connection;
                this.setToolTipText(connection);
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static class AvgSpeedCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final FileHistoryItem item = (FileHistoryItem) table.getValueAt(row, -1);
            final float averageSpeed = item.getAverageSpeed();

            if (averageSpeed <= 0) {
                value = "";
                this.setToolTipText(null);
            } else {
                value = ContentPanel.bytesToAnother((long) averageSpeed) + "/s";
                this.setToolTipText(value.toString());
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static class AvgSpeedColumnComparator implements Comparator<FileHistoryItem> {
        @Override
        public int compare(FileHistoryItem o1, FileHistoryItem o2) {
            return Float.compare(o1.getAverageSpeed(), o2.getAverageSpeed());
        }
    }

    private static class SizeColumnComparator implements Comparator<Long> {
        @Override
        public int compare(Long o1, Long o2) {
            return o1.compareTo(o2);
        }
    }

    private JCheckBox checkbox;
    private JComboBox combobox;
    private JTextField fieldFilter;
    private JLabel fileCount;
    private JLabel totalDownloads;
    private JXTable table;
    private JButton clearHistoryBtn;
    private JButton okButton;


    private static enum DownloadsFilters {
        ALL_DOWNLOADS, TODAY, YESTERDAY, LAST_WEEK, LAST_MONTH, THREE_MONTHS, SIX_MONTHS, LAST_YEAR
    }

}