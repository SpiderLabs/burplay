package burp;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.List;

/**
 * Created by mtalecki on 05/07/2017.
 */



public class BurpExtender implements IBurpExtender, IContextMenuFactory, ITab {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    private PrintStream printStream;
    private PrintStream errorStream;

    // main panel
    private final JPanel panelMain = new JPanel();

    private String version;

    // progress panel
    private final JPanel panelProgress = new JPanel();
    private final JProgressBar barProgress = new JProgressBar();
    private final JButton buttonReplay = new JButton();
    private final JButton buttonReset = new JButton();

    // replay table and controller
    private final ReplayTableController replayTableController = new ReplayTableController();
    private final ReplayTable replayTable = new ReplayTable(replayTableController);

    // modification table and controller
    private final ModificationTableController modificationTableController = new ModificationTableController();
    private final ModificationTable modificationTable = new ModificationTable(modificationTableController);
    private final ModificationConfig modificationConfig = new ModificationConfig();

    // session table and controller
    private final SessionTableController sessionTableController = new SessionTableController();
    private final SessionTable sessionTable = new SessionTable(sessionTableController);

    // details tabbed pane
    private ReplayDetailsTabbedPane tabbedReplayDetails;

    public void registerExtenderCallbacks (IBurpExtenderCallbacks callbacks) {

        // keep a reference to our callbacks object
        this.callbacks = callbacks;

        // obtain an extension helpers object
        this.helpers = callbacks.getHelpers();

        // set our extension name
        this.callbacks.setExtensionName("Burplay");

        // context menu
        callbacks.registerContextMenuFactory(this);

        // stdout / stderr
        OutputStream stdOut = callbacks.getStdout();
        OutputStream stdErr = callbacks.getStderr();
        printStream = new PrintStream(stdOut);
        errorStream = new PrintStream(stdErr);

        Properties properties = new Properties();

        try {
            properties.load(this.getClass().getResourceAsStream("/Burplay.properties"));
            version = "v." + properties.getProperty("version");
        } catch (Exception e) {
            version = "";
        }

        // welcome message
        log(String.format("Burplay %s\nMichal Talecki <mtalecki@trustwave.com>", version));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setUI();
            }
        });
    }

    private void setUI() {
        JPanel panelLeft = new JPanel();
        JPanel panelRight = new JPanel();

        // mainSplitPane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelLeft, panelRight);
        mainSplitPane.setResizeWeight(0);
        panelMain.setLayout(new BorderLayout());
        panelMain.add(mainSplitPane, BorderLayout.CENTER);

        // panelLeft
        panelLeft.setLayout(new BorderLayout());
        panelLeft.setMinimumSize(new Dimension(500,200));

        // panel progress
        panelProgress.setLayout(new BoxLayout(panelProgress, BoxLayout.X_AXIS));
        panelProgress.add(barProgress);
        panelProgress.add(buttonReplay);

        // button reset
        buttonReset.setAction(new ResetAction());
        buttonReset.setText("Reset");

        // button replay
        buttonReplay.setAction(new ReplayAction());
        buttonReplay.setActionCommand("replay");
        buttonReplay.setText("REPLAY!");

        // panel top (box layout)
        JPanel panelTop = new JPanel();
        panelTop.setLayout(new BoxLayout(panelTop, BoxLayout.X_AXIS));


        JButton buttonDoc = new JButton();
        buttonDoc.addActionListener(new HelpAction());
        buttonDoc.setText("?");
        panelTop.add(buttonDoc);
        panelTop.add(buttonReset);
        panelTop.add(panelProgress);

        // panelTop is PAGE START in panelLeft's BorderLayout
        panelLeft.add(panelTop, BorderLayout.PAGE_START);


        JScrollPane replayScrollPane = new JScrollPane(replayTable);
        panelLeft.add(replayScrollPane, BorderLayout.CENTER);

        JScrollPane modificationScrollPane = new JScrollPane(modificationTable);
        JScrollPane sessionScrollPane = new JScrollPane(sessionTable);


        JPanel panelConfig = new JPanel();
        panelConfig.setLayout(new BoxLayout(panelConfig, BoxLayout.Y_AXIS));


        // panel modifications
        JPanel panelModificationsAll = new JPanel();
        TitledBorder titleModifications;
        titleModifications = BorderFactory.createTitledBorder("Modifications");
        panelModificationsAll.setBorder(titleModifications);
        panelModificationsAll.setLayout(new BoxLayout(panelModificationsAll, BoxLayout.Y_AXIS));

        JPanel panelModifications = new JPanel();
        panelModifications.setLayout(new BoxLayout(panelModifications, BoxLayout.X_AXIS));

        // button remove modification
        JButton buttonRemoveModification = new JButton();
        buttonRemoveModification.setAction(new AddRemoveAction());
        buttonRemoveModification.setText("Remove");
        buttonRemoveModification.setActionCommand("removemod");

        panelModifications.add(modificationScrollPane);
        panelModifications.add(buttonRemoveModification);

        Dimension dimMod = modificationTable.getPreferredSize();
        modificationScrollPane.setPreferredSize(
                new Dimension(400,modificationTable.getRowHeight()*7+1));
        modificationScrollPane.setMaximumSize(
                new Dimension(400, modificationTable.getRowHeight()*7+1));

        panelModificationsAll.add(panelModifications);
        panelModificationsAll.add(modificationConfig);




        // panel sessions
        JPanel panelSessionsAll = new JPanel();
        TitledBorder titleSessions;
        titleSessions = BorderFactory.createTitledBorder("Sessions");
        panelSessionsAll.setBorder(titleSessions);
        panelSessionsAll.setLayout(new BoxLayout(panelSessionsAll, BoxLayout.Y_AXIS));

        JPanel panelSessions = new JPanel();
        panelSessions.setLayout(new BoxLayout(panelSessions, BoxLayout.X_AXIS));

        // button remove session
        JPanel panelSessionButtons = new JPanel();
        panelSessionButtons.setLayout(new BoxLayout(panelSessionButtons, BoxLayout.Y_AXIS));

        JButton buttonRemoveSession = new JButton();
        buttonRemoveSession.setAction(new AddRemoveAction());
        buttonRemoveSession.setText("Remove");
        buttonRemoveSession.setActionCommand("removeses");

        JButton buttonApplySession = new JButton();
        buttonApplySession.setAction(new AddRemoveAction());
        buttonApplySession.setText("Apply");
        buttonApplySession.setActionCommand("applyses");

        panelSessionButtons.add(buttonApplySession);
        panelSessionButtons.add(buttonRemoveSession);


        panelSessions.add(sessionScrollPane);
        panelSessions.add(panelSessionButtons);

        Dimension dimSes = sessionTable.getPreferredSize();
        sessionScrollPane.setPreferredSize(
                new Dimension(500,sessionTable.getRowHeight()*7+1));
        sessionScrollPane.setMaximumSize(
                new Dimension(500, sessionTable.getRowHeight()*7+1));

        panelSessions.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        panelSessionsAll.add(panelSessions);
        panelSessionsAll.add(new JPanel()); // sizing hack


        // panel config
        panelConfig.add(new JSeparator(SwingConstants.HORIZONTAL));
        panelConfig.add(panelModificationsAll);

        panelConfig.add(new JSeparator(SwingConstants.HORIZONTAL));
        panelConfig.add(panelSessionsAll);

        //panelConfig.setBorder(BorderFactory.createEmptyBorder(30,30,30,30));
        panelLeft.add(panelConfig, BorderLayout.PAGE_END);





        // panelRight
        panelRight.setLayout(new BorderLayout());
        tabbedReplayDetails = new ReplayDetailsTabbedPane();
        tabbedReplayDetails.addReplayTab("Original");
        panelRight.add(tabbedReplayDetails);



        callbacks.customizeUiComponent(panelMain);
        // these should not be customized explicitly (should propagate from panelMain)
        //callbacks.customizeUiComponent(panelLeft);
        //callbacks.customizeUiComponent(panelRight);
        //callbacks.customizeUiComponent(panelRequest);
        //callbacks.customizeUiComponent(panelResponse);
        //callbacks.customizeUiComponent(replayScrollPane);

        callbacks.addSuiteTab(BurpExtender.this);
    }

    public class ReplayWorker extends SwingWorker<Void, Integer> {

        private List<IHttpRequestResponse> inputRequestResponseList;
        private List<IHttpRequestResponse> outputRequestResponseList;

        public ReplayWorker(List<IHttpRequestResponse> inp, List<IHttpRequestResponse> outp) {
            inputRequestResponseList = inp;
            outputRequestResponseList = outp;
        }

        private IHttpRequestResponse makeRequest(IHttpService s,  byte[] req) {
            IHttpRequestResponse rr = null;
            try {
                rr = callbacks.makeHttpRequest(s, req);
            } catch (Exception e) {
                logError(e.toString() +": Error while requesting from " + s.getHost());
            }
            return rr;
        }

        @Override
        protected void process(List<Integer> progress) {
            barProgress.setValue(progress.get(progress.size() - 1));
        }

        @Override
        protected Void doInBackground() throws Exception {

            //log("ReplayWorker thread started...");

            List<ReplayModification> listMods = modificationTableController.listModifications;

            for (int i = 0; i < inputRequestResponseList.size(); i++) {
                if (this.isCancelled()) {
                    return null;
                }
                publish(i + 1);

                byte[] newReq = inputRequestResponseList.get(i).getRequest();

                for (int m = 0; m<listMods.size(); m++) {
                    ReplayModification mod = listMods.get(m);
                    newReq = mod.processRequest(newReq);
                }

                IHttpRequestResponse reqRes = makeRequest(inputRequestResponseList.get(i).getHttpService(), newReq);

                outputRequestResponseList.add(reqRes);

            }

            return null;
        }

        @Override
        protected void done() {
            super.done();
            buttonReplay.setActionCommand("replay");
            buttonReplay.setText("REPLAY!");
            barProgress.setValue(0);
            barProgress.setMaximum(0);
            barProgress.setStringPainted(false);
            buttonReset.setEnabled(true);
            int selected = replayTable.lastSelectedRow;
            int numRows = replayTable.getRowCount();
            if (selected >= 0 && selected < numRows) {
                replayTable.addRowSelectionInterval(selected, selected);
            }
        }

    }

    private class ReplayAction extends AbstractAction {

        private ReplayWorker replayWorker;


        @Override
        public void actionPerformed(ActionEvent e) {
            //log("replay action");

            if (e.getActionCommand().equals("cancel")) {
                replayWorker.cancel(true);
                replayWorker = null;
                return;
            }

            // base requests
            ReplayDetailsPanel panelCurrentDetails = tabbedReplayDetails.getOriginalDetailsPanel();
            List<IHttpRequestResponse> replayList = panelCurrentDetails.listHTTPRequestsResponses;

            // don't proceed with empty list
            if (replayList.size() == 0) {
                return;
            }

            // new tab
            // TODO give more specific name to a new tab
            ReplayDetailsPanel newDetailsPanel = tabbedReplayDetails.addReplayTab(null);
            tabbedReplayDetails.setSelectedIndex(tabbedReplayDetails.getTabCount()-1);
            List<IHttpRequestResponse> outputReplayList  = newDetailsPanel.listHTTPRequestsResponses;

            // disable button so next replay can't be requested until the current one is done
            buttonReplay.setActionCommand("cancel");
            buttonReplay.setText("Cancel");


            buttonReset.setEnabled(false);
            barProgress.setMinimum(0);
            barProgress.setMaximum(replayList.size());
            barProgress.setStringPainted(true);
            replayWorker = new ReplayWorker(replayList, outputReplayList);
            replayTable.lastSelectedRow = replayTable.getSelectedRow();
            replayWorker.execute();

            //buttonReplay.setEnabled(true);
            //log("Requests done, updating model...");
            replayTableController.updateTableModel();
            //log ("Model updated.");
        }
    }


    private void log(String text) {
        printStream.println(text);
        printStream.flush();
    }

    private void logError(String text) {
        errorStream.println(text);
        errorStream.flush();
    }

    private class ModificationConfig extends JPanel implements ActionListener {

        private JComboBox<String> comboParamType;
        private JTextField fieldParamName;
        private JCheckBox chkboxRemove;
        private JTextField fieldParamValue;
        private JButton buttonAddModification;

        public ModificationConfig() {
            comboParamType = new JComboBox<>();
            comboParamType.addItem("Header");
            comboParamType.addItem("Cookie");
            comboParamType.addItem("GET");
            comboParamType.addItem("POST");
            fieldParamName = new JTextField(10);
            fieldParamName.setToolTipText("Header/cookie/param name");
            chkboxRemove = new JCheckBox();
            chkboxRemove.setToolTipText("Remove this header/cookie/param");
            chkboxRemove.setSelected(false);
            chkboxRemove.addActionListener(this);
            fieldParamValue = new JTextField(10);
            fieldParamValue.setToolTipText("Header/cookie/param value");
            buttonAddModification = new JButton();
            buttonAddModification.setAction(new AddRemoveAction());
            buttonAddModification.setText("Add");
            buttonAddModification.setActionCommand("addmod");


            this.add(comboParamType);
            this.add(fieldParamName);
            this.add(chkboxRemove);
            this.add(fieldParamValue);
            this.add(buttonAddModification);

        }

        @Override
        public void actionPerformed(ActionEvent e) {
            fieldParamValue.setEnabled(!chkboxRemove.isSelected());
        }
    }

    private class ModificationTableController extends AbstractTableModel {

        public final List<ReplayModification> listModifications = new ArrayList<>();
        private final List<String[]> data = new ArrayList<>();
        private String[] columnNames = {"Type", "Name", "Value"};

        public void addModification(ReplayModification mod) {
            listModifications.add(mod);
            updateTableModel();
        }

        public void removeModification(int index) {
            listModifications.remove(index);
            updateTableModel();
        }

        public void updateTableModel() {
            data.clear();

            for (int i = 0; i < listModifications.size(); i++) {
                ReplayModification mod = listModifications.get(i);
                String[] row = new String[3];
                row[0] = mod.modTypeString;
                row[1] = mod.modName;
                row[2] = mod.modValue;
                data.add(row);
            }
            //log("updating model...");
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String[] row = data.get(rowIndex);
            if (columnIndex == 2 && row[columnIndex] == null) {
                return "-- REMOVE --";
            }
            return row[columnIndex];
        }
    }

    private class ModificationTable extends JTable {
        public ModificationTable(ModificationTableController modificationTableController) {
            super(modificationTableController);
            this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }
    }

    private class BurplaySession {
        String sessionName;
        String cookieName;
        String cookieValue;

        public BurplaySession (String sName, String cName, String cValue) {
            sessionName = sName;
            cookieName = cName;
            cookieValue = cValue;
        }
    }

    private class SessionTableController extends AbstractTableModel {

        public final List<BurplaySession> listSessions = new ArrayList<>();
        private final List<String[]> data = new ArrayList<>();
        private String[] columnNames = {"Session Name", "Cookie Name", "Cookie Value"};

        public void addSession(BurplaySession s) {
            listSessions.add(s);
            updateTableModel();
        }

        public void removeSession(int index) {
            listSessions.remove(index);
            updateTableModel();
        }

        public void updateTableModel() {
            data.clear();

            for (int i = 0; i < listSessions.size(); i++) {
                BurplaySession s = listSessions.get(i);
                String[] row = new String[3];
                row[0] = s.sessionName;
                row[1] = s.cookieName;
                row[2] = s.cookieValue;
                data.add(row);
            }
            //log("updating model...");
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String[] row = data.get(rowIndex);
            return row[columnIndex];
        }
    }

    private class SessionTable extends JTable {
        public SessionTable(SessionTableController sessionTableController) {
            super(sessionTableController);
            this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }
    }


    private class ReplayContextMenu extends JPopupMenu implements ActionListener {
        JMenuItem miRemoveRequests;
        ReplayContextMenu() {
            super();
            miRemoveRequests = new JMenuItem("Remove request(s)");
            miRemoveRequests.setActionCommand("removeRequests");
            miRemoveRequests.addActionListener(this);
            this.add(miRemoveRequests);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String actCommand = e.getActionCommand();
            if (actCommand == "removeRequests") {
                replayTableController.removeRequests(replayTable.getSelectedRows());
            }
        }
    }

    private class ReplayTable extends JTable implements MouseListener{
        private int lastSelectedRow = 0;
        public ReplayTable(TableModel tableModel) {
            super(tableModel);
            this.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            this.getTableHeader().setReorderingAllowed(true);
            //this.getTableHeader().setResizingAllowed(false);
//            this.setRowSelectionAllowed(true);
//            this.setCellSelectionEnabled(false);
//            this.setColumnSelectionAllowed(false);
            this.selectionModel.addListSelectionListener((ReplayTableController)tableModel);
            TableColumn indexColumn = this.getColumnModel().getColumn(0);
            indexColumn.setMaxWidth(40);
            TableColumn methodColumn  = this.getColumnModel().getColumn(1);
            methodColumn.setMaxWidth(60);
            this.addMouseListener(this);
        }

        @Override
        public void mouseClicked(MouseEvent e) {

        }

        @Override
        public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    ReplayContextMenu cm = new ReplayContextMenu();
                    cm.show(e.getComponent(), e.getX(), e.getY());
                }
        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }
    }

    // JPanel
    @Override
    public String getTabCaption() {
        return "Replay";
    }

    @Override
    public Component getUiComponent() {
        return panelMain;
    }

    // IContextMenuFactory
    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {

        byte invocationContext = invocation.getInvocationContext();

        if (invocationContext == IContextMenuInvocation.CONTEXT_PROXY_HISTORY ||
                invocationContext == IContextMenuInvocation.CONTEXT_TARGET_SITE_MAP_TABLE ) {
            List<JMenuItem> menu = new ArrayList<>();
            Action sendToReplayAction = new SendToReplayAction("Send to Replay", invocation);
            JMenuItem sendToReplayMenuItem = new JMenuItem(sendToReplayAction);
            menu.add(sendToReplayMenuItem);
            return menu;
        }

        if (invocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
                invocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST ||
                invocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE ||
                invocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE) {
            List<JMenuItem> menu = new ArrayList<>();
            Action defineSessionAction = new DefineSessionAction("Define Burplay session", invocation);
            JMenuItem defineSessionMenuItem = new JMenuItem(defineSessionAction);
            menu.add(defineSessionMenuItem);
            return menu;
        }
        return null;
    }

    class DefineSessionAction extends AbstractAction {

        IContextMenuInvocation invocation;

        public DefineSessionAction(String text, IContextMenuInvocation invocation) {
            super(text);
            this.invocation = invocation;
        }

        // this handles defining session
        @Override
        public void actionPerformed(ActionEvent e) {
            //log("defining session");
            IHttpRequestResponse[] reqsResps = invocation.getSelectedMessages();
            if (reqsResps.length != 1) { return; }
            int[] bounds = invocation.getSelectionBounds();
            if (bounds.length != 2 || bounds[0] == bounds[1]) { return; }

            byte context = invocation.getInvocationContext();
            byte[] messageContent;
            if (context == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
                context == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST) {
                messageContent = reqsResps[0].getRequest();
            } else {
                messageContent = reqsResps[0].getResponse();
            }

            String selected = helpers.bytesToString(messageContent).substring(bounds[0], bounds[1]);

            String[] cookieData = selected.split("=");
            if (cookieData.length != 2) { return; }

            String dialogMessage = String.format("Enter name for the following session cookie details:\n\n%s\n\n", selected);

            String sesName = JOptionPane.showInputDialog((Component)e.getSource(), dialogMessage,"Define Burplay session", JOptionPane.QUESTION_MESSAGE);
            if (sesName == null) {
                return;
            }

            BurplaySession newSession = new BurplaySession(sesName, cookieData[0], cookieData[1]);
            sessionTableController.addSession(newSession);
        }
    }


    // this is a table model but also a controller for details tabs
    class ReplayTableController extends AbstractTableModel implements ListSelectionListener {
        //private final String[] columnNames = {"Host", "Method", "URL"};
        private final String[] columnNames = {"#","Method", "URL"};
        //public final List<IHttpRequestResponse> replayTable = new ArrayList<>();

        // list for table
        private final List<String[]> data = new ArrayList<>();

        public void addRequestsResponses(IHttpRequestResponse[] rrs) {
            // update tabbed details
            for (IHttpRequestResponse rr: rrs) {
                tabbedReplayDetails.addRequestResponse(rr);
            }
            // update table model
            updateTableModel();
        }

        public void resetTable() {
            data.clear();
            fireTableDataChanged();
            tabbedReplayDetails.resetTabs();
        }

        // TODO make it better
        public void removeRequests(int[] rows) {
            tabbedReplayDetails.removeRequests(rows);
            this.updateTableModel();
        }


        public void updateTableModel() {
            data.clear();
            // this is safe because we update this one first
            List<IHttpRequestResponse> rrs = tabbedReplayDetails.getOriginalDetailsPanel().listHTTPRequestsResponses;

            for (int i = 0; i < rrs.size(); i++) {
                IHttpRequestResponse reqres = rrs.get(i);
                //IHttpService httpService = reqres.getHttpService();
                IRequestInfo reqInfo = helpers.analyzeRequest(reqres);
                String method = reqInfo.getMethod();
                String url = reqInfo.getUrl().toString();
                String[] row = new String[3];
                row[0] = Integer.toString(i+1);
                row[1] = method;
                row[2] = url;
                data.add(row);
            }
            //log("updating model...");
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            if (data == null) {
                return 0;
            }
            return data.size();
        }

        public String getColumnName(int c) {
            return columnNames[c];
        }

        @Override
        public int getColumnCount() {
            return 3;
        }


        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return data.get(rowIndex)[columnIndex];
        }

        public void valueChanged(ListSelectionEvent e) {
            ListSelectionModel lsm = (ListSelectionModel) e.getSource();
            int selected = lsm.getMinSelectionIndex();
            if (selected >= 0) {
                tabbedReplayDetails.chooseRequestNo(selected);

            }
        }

    }

    class SendToReplayAction extends AbstractAction {

        IContextMenuInvocation invocation;

         public SendToReplayAction(String text, IContextMenuInvocation invocation) {
            super(text);
            this.invocation = invocation;
        }

        // this handles adding requests to the extension
        @Override
        public void actionPerformed(ActionEvent e) {
            IHttpRequestResponse[] selectedReqsResps = this.invocation.getSelectedMessages();
            replayTableController.addRequestsResponses(selectedReqsResps);
        }
    }

    class ResetAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            int yesno = JOptionPane.showConfirmDialog(panelMain, "Remove all requests?",
                    "Reset Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (yesno == JOptionPane.NO_OPTION) {
                return;
            }
            replayTableController.resetTable();
        }
    }

    class HelpAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            FormHelp formHelp = new FormHelp();
            formHelp.pack();
            formHelp.setVisible(true);
        }
    }

    class AddRemoveAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            String actionCommand = e.getActionCommand();
            switch(actionCommand) {
                case "addmod":
                    String t = modificationConfig.comboParamType.getSelectedItem().toString();
                    String n = modificationConfig.fieldParamName.getText();
                    String v = (modificationConfig.chkboxRemove.isSelected())?null:modificationConfig.fieldParamValue.getText();
                    ReplayModification modAdd = new ReplayModification(t, n, v);
                    modificationTableController.addModification(modAdd);
                    modificationConfig.fieldParamName.setText("");
                    modificationConfig.chkboxRemove.setSelected(false);
                    modificationConfig.fieldParamValue.setEnabled(true);
                    modificationConfig.fieldParamValue.setText("");
                    break;
                case "removemod":
                    int rowMod = modificationTable.getSelectedRow();
                    if (rowMod >= 0 ) {
                        modificationTableController.removeModification(rowMod);
                    }
                    break;
                case "removeses":
                    int rowSes = sessionTable.getSelectedRow();
                    if (rowSes >= 0) {
                        sessionTableController.removeSession(rowSes);
                    }
                    break;
                case "applyses":
                    int rowSesApply = sessionTable.getSelectedRow();
                    BurplaySession ses;
                    if (rowSesApply >= 0) {
                        ses = sessionTableController.listSessions.get(rowSesApply);
                    } else {
                        return;
                    }
                    ReplayModification modApply = new ReplayModification("Cookie", ses.cookieName, ses.cookieValue);
                    modificationTableController.addModification(modApply);
                    break;
            }

        }
    }

    class ReplayDetailsTabbedPane extends JTabbedPane {

        public int currentTab = 1;

        public ReplayDetailsTabbedPane() {
            super();
        }

        public ReplayDetailsPanel addReplayTab(String name) {

            ReplayDetailsPanel panel = new ReplayDetailsPanel();
            if (name == null) {
                name = Integer.toString(currentTab);
                currentTab++;
            }

//            if (parentName != null) {
//                parentName = parentName.replaceAll("\\(.*\\)", "");
//                name += String.format("(%s)", parentName);
//            }

            this.addTab(name, panel);
            return panel;
        }

        public void removeRequests(int[] rows) {
            int tabCount = this.getTabCount();
            for (int i = 0; i<tabCount; i++) {
                ReplayDetailsPanel dp = (ReplayDetailsPanel) this.getComponentAt(i);
                dp.removeRequests(rows);
            }
        }

        public void resetTabs() {
            int tabCount = this.getTabCount();
            //TODO shouldn't destroy them or something?
            for (int i=tabCount-1; i>0 ; i--) {
                this.remove(i);
            }
            this.updateUI();
            this.getOriginalDetailsPanel().listHTTPRequestsResponses.clear();
            this.getOriginalDetailsPanel().updateMessages(-1);
            currentTab = 1;
        }

        // TODO this should add request and response to the original reqres tab
        // TODO should also add the request to other tabs, so they can issue them and get responses
        public void addRequestResponse(IHttpRequestResponse reqres) {
            ReplayDetailsPanel panelOriginalDetails = (ReplayDetailsPanel) this.getComponentAt(0);
            panelOriginalDetails.addRequestResponse(reqres);
        }


        public ReplayDetailsPanel getOriginalDetailsPanel() {
            ReplayDetailsPanel panelDetails = (ReplayDetailsPanel) this.getComponentAt(0);
            return panelDetails;
        }

        public ReplayDetailsPanel getCurrentDetailsPanel() {
            ReplayDetailsPanel panelDetails = (ReplayDetailsPanel) this.getSelectedComponent();
            return panelDetails;
        }


        public void chooseRequestNo(int n) {
            for (int i = 0; i<this.getTabCount(); i++) {
                ReplayDetailsPanel dp = (ReplayDetailsPanel) this.getComponentAt(i);
                dp.updateMessages(n);
            }
        }

    }

    class MessageEditorController implements IMessageEditorController {

        private IHttpService httpService = null;
        private byte[] request = new byte[0];
        private byte[] response = new byte[0];

        public void update(IHttpService hs, byte[] req, byte[] res) {
            httpService = hs;
            request = req;
            response = res;
        }

        @Override
        public IHttpService getHttpService() {
            return httpService;
        }

        @Override
        public byte[] getRequest() {
            return request;
        }

        @Override
        public byte[] getResponse() {
            return response;
        }
    }

    class ReplayDetailsPanel extends JPanel {

        private List<IHttpRequestResponse> listHTTPRequestsResponses = new ArrayList<>();
        private IMessageEditor messageEditorRequest;
        private IMessageEditor messageEditorResponse;
        private MessageEditorController messageEditorController = new MessageEditorController();

        public ReplayDetailsPanel() {
            super();
            messageEditorRequest = callbacks.createMessageEditor(messageEditorController, false);
            messageEditorResponse = callbacks.createMessageEditor(messageEditorController, false);
            JPanel panelRequest = new JPanel();
            JPanel panelResponse = new JPanel();

            JSplitPane detailsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    panelRequest, panelResponse);
            detailsSplitPane.setResizeWeight(0.5);
            panelRequest.setLayout(new BorderLayout());
            panelResponse.setLayout(new BorderLayout());

            panelRequest.add(messageEditorRequest.getComponent());
            panelResponse.add(messageEditorResponse.getComponent());

            this.setLayout(new BorderLayout());
            this.add(detailsSplitPane, BorderLayout.CENTER);
        }

        public void addRequestResponse(IHttpRequestResponse reqres) {
            this.listHTTPRequestsResponses.add(reqres);
        }

        public void removeRequest(int row) {
            listHTTPRequestsResponses.remove(row);
        }

        // TODO fixit
        public void removeRequests(int[] rows) {
            List<IHttpRequestResponse> newList = new ArrayList<>();
            for (int i=0; i<listHTTPRequestsResponses.size(); i++) {
                boolean shouldCopy = true;
                for (int j=0; j<rows.length; j++) {
                    if (i == rows[j]) {
                        shouldCopy = false;
                        break;
                    }
                }
                if (shouldCopy) {
                    newList.add(listHTTPRequestsResponses.get(i));
                }
            }
            listHTTPRequestsResponses = newList;
        }

        public void updateMessages(int index) {
            if (index == -1) {
                messageEditorRequest.setMessage(new byte[0], true);
                messageEditorResponse.setMessage(new byte[0], false);
                messageEditorController.update(null, new byte[0], new byte[0]);
            }

            try {
                IHttpRequestResponse reqres = listHTTPRequestsResponses.get(index);
                messageEditorRequest.setMessage(reqres.getRequest(), true);
                messageEditorResponse.setMessage(reqres.getResponse(), false);
                messageEditorController.update(reqres.getHttpService(), reqres.getRequest(), reqres.getResponse());
            } catch(IndexOutOfBoundsException ioob) {
                // this happens when there is no req/res for this replay round
                messageEditorRequest.setMessage(new byte[0], true);
                messageEditorResponse.setMessage(new byte[0], false);
                messageEditorController.update(null, new byte[0], new byte[0]);
            } catch (Exception e) {
                // this happens when there was a problem with reqres
                messageEditorRequest.setMessage(new byte[0], true);
                messageEditorResponse.setMessage(new byte[0], false);
                messageEditorController.update(null, new byte[0], new byte[0]);
            }
        }

    }

    public enum ModType {
        Header, Cookie, GET, POST
    }

    class ReplayModification {

        public ModType modType;
        public String modTypeString;
        public String modName;
        public String modValue;
        private byte paramType;

        // TODO this has to be applied to all requests and stored in ReplayDetailsPanel for each replay
        // TODO it should be possible to apply these to requests which have not been replayed (after adding new ones)


        public ReplayModification(String t, String n, String v) {
            switch(t) {
                case "Header":
                    modType = ModType.Header;
                    break;
                case "Cookie":
                    modType = ModType.Cookie;
                    paramType = IParameter.PARAM_COOKIE;
                    break;
                case "GET":
                    modType = ModType.GET;
                    paramType = IParameter.PARAM_URL;
                    break;
                case "POST":
                    modType = ModType.POST;
                    paramType = IParameter.PARAM_BODY;
                    break;
            }
            modTypeString = t;
            modName = n;
            modValue = v;
        }

        private boolean isOfType(byte burpType) {
            if (modType == ModType.GET && burpType == IParameter.PARAM_URL) {
                return true;
            }
            if (modType == ModType.POST && burpType == IParameter.PARAM_BODY) {
                return true;
            }
            if (modType == ModType.Cookie && burpType == IParameter.PARAM_COOKIE) {
                return true;
            }
            return false;
        }

        public byte[] processRequest(byte[] inputRequest) {

            IParameter newParam = null;

            IRequestInfo requestInfo = helpers.analyzeRequest(inputRequest);

            byte [] newReq = null;

            if (modType == ModType.Header) {
                // this is Header
                List<String> headers = requestInfo.getHeaders();
                Iterator<String> iterator = headers.iterator();
                // TODO collection.removeIf
                while (iterator.hasNext()) {
                    if (iterator.next().startsWith(modName + ":")) {
                        iterator.remove();
                    }
                }
                // modValue == null means remove
                if (modValue != null) {
                    headers.add(String.format("%s: %s", modName, modValue));
                }
                byte[] body = Arrays.copyOfRange(inputRequest, requestInfo.getBodyOffset(), inputRequest.length);
                newReq = helpers.buildHttpMessage(headers, body);
            } else {
                // this is Cookie, GET or POST
                newReq = inputRequest.clone();
                List<IParameter> params = requestInfo.getParameters();
                for (IParameter p: params) {
                    if (p.getName().equals(modName) && this.isOfType(p.getType())) {
                        newReq = helpers.removeParameter(newReq,p);
                    }
                }
                // modValue == null means remove
                if (modValue != null) {
                    newParam = helpers.buildParameter(modName, modValue, paramType);
                    newReq = helpers.addParameter(newReq, newParam);
                }
            }
            return newReq;
        }

    }
}


