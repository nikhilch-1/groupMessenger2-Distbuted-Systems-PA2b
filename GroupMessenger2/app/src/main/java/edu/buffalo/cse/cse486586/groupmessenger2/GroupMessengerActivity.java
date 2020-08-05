
//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//===========================================================================================================
package edu.buffalo.cse.cse486586.groupmessenger2;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */

public class GroupMessengerActivity extends Activity {

    public class Node implements Comparable<Node> {
        public int seq;
        public int processNo;
        public String status;
        public String message;

        public Node(int s, int p, String m) {
            this.seq = s;
            this.status = "N";
            this.processNo = p;
            this.message = m;
        }

        public int getSeq() {
            return this.seq;
        }

        public int getProcessNo() {
            return this.processNo;
        }

        public String getMessage(){
            return this.message;
        }

        public void setStatus() {
            this.status = "Y";
        }

        public void updateSeq(int k){
            this.seq=k;
        }

        @Override
        public int compareTo(Node k) {
            //If the assigned sequence numbers are same, then compare and decide the process numbers.
            if (this.getSeq() == k.getSeq()) {
                return this.getProcessNo() - k.getProcessNo();
            }
            //else just decide based on sequence numbers.
            return this.getSeq() - k.getSeq();
        }
    }

    private Uri mUri = null;
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    public HashMap<String, String> m = new HashMap();
    public int proposal = 0;
    public PriorityQueue<Node> q = new PriorityQueue<Node>();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    int agreement;
    String retainedMsg;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

//         * Calculate the port number that this AVD listens on.
//         * It is just a hack that I came up with to get around the networking limitations of AVDs.
//         * The explanation is provided in the PA1 spec.
//         *  @author steve ko
//         */


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        m.put(REMOTE_PORT0, "1");
        m.put(REMOTE_PORT1, "2");
        m.put(REMOTE_PORT2, "3");
        m.put(REMOTE_PORT3, "4");
        m.put(REMOTE_PORT4, "5");

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final Button sendButton = (Button) findViewById(R.id.button4);
        final EditText editText = (EditText) findViewById(R.id.editText1);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.

                //Commenting this since I only need to see the received messages.
//                TextView localTextView = (TextView) findViewById(R.id.textView1);
//                localTextView.append("\t" + msg); // This is one way to display a string.
//                TextView remoteTextView = (TextView) findViewById(R.id.textView1);
//                remoteTextView.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, m.get(myPort) +":"+ msg, myPort);
                Log.i(TAG, "Message sent by: " + myPort +"->"+ m.get(myPort));
            }
        });
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        int counter = 0;
        ContentValues keyValueToInsert = new ContentValues();
        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                String receivedMsg;
                String sender="";
                while (true) {
                    Socket socket = serverSocket.accept();
                    BufferedReader inComing = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter outGoing = new PrintWriter(socket.getOutputStream(), true);
                    DataInputStream incomingAgreement=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    if ((receivedMsg= inComing.readLine()) != null) {
                        String[] msgSplit=receivedMsg.split(":");       //https://developer.android.com/reference/java/lang/String
                        sender=msgSplit[1];
                        retainedMsg = msgSplit[2];
                        String myPNo=msgSplit[0];
                        Log.i(TAG,"SP1: Msg from "+sender+": "+receivedMsg);
                        Node newEntry=new Node(proposal,Integer.parseInt(msgSplit[0]),retainedMsg);
                        q.add(newEntry);
                        Log.i(TAG,"SP1: Added to priority queue with proposal: "+proposal);
                        outGoing.println(myPNo+":"+proposal);
                        outGoing.flush();
                        proposal+=1;
                        Log.i(TAG,"SP2: Proposal sent from "+myPNo+" to "+sender+" for "+retainedMsg);
                    }


                    // Agreement part
                    while (true) {
                        Log.i(TAG,"SP3: Expecting an agreement number from "+sender);
                        try{
                            receivedMsg = incomingAgreement.readUTF();

                            String[] agreedSplits = receivedMsg.split(":");     //https://developer.android.com/reference/java/lang/String
                            agreement = Integer.parseInt(agreedSplits[1]);
                            String forMsg = agreedSplits[2];
                            Log.i(TAG, "SP3: Agreement received from " + agreedSplits[0] + " for " + forMsg);

                            Iterator<Node> qIterator=q.iterator();                             //https://developer.android.com/reference/java/util/Iterator
                            while (qIterator.hasNext()) {
                                if (qIterator.next().message.equals(forMsg)) {
                                    qIterator.next().updateSeq(agreement);
                                    qIterator.next().setStatus();
                                }
                            }
                            publishProgress(retainedMsg);
                            socket.close();
                            break;
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "SP3 Error: Number Format Exception");
                        } catch (Exception e){
                            // AVD Failure Handling Part
                            Log.e(TAG,"SP3 Error: Sender AVD"+sender+" has failed");
                            try {
                                Log.i(TAG,"SP4: Handling Sender failure...");
                                Log.i(TAG,"Removing: "+retainedMsg);
                                Node temp=new Node(proposal-1, Integer.parseInt(sender), retainedMsg);
                                q.remove(temp);
                                Log.i(TAG,"SP5: Message "+retainedMsg+" has been deleted.");
                                break;
                            }catch(Exception i){
                                Log.e(TAG,"SP3 Error: Could not handle Sender failure.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Server: Not able to receive messages");
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);   //using same textview for both received and sent messages
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            keyValueToInsert.put(KEY_FIELD, counter);
            keyValueToInsert.put(VALUE_FIELD, strReceived);
            getContentResolver().insert(mUri, keyValueToInsert);

            String filename = Integer.toString(counter++);
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
                Log.i(TAG,"Message "+strReceived+" stored as "+filename);
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            Log.i(TAG,"Message transaction complete.");
            return;
        }
    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                Socket[] socketList=new Socket[5];
                String[] portList = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
                String msgToSend = msgs[0];
                String[] originalMsgSplits=msgToSend.split(":");  //https://developer.android.com/reference/java/lang/String
                List<Integer> proposalList=new ArrayList<Integer>();
                int sum=0;
                for (int i = 0; i < portList.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(portList[i]));
                    socketList[i]=socket;
                    String sending=m.get(portList[i])+":"+msgToSend;
                    PrintWriter outGoing = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader inComing = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    /*
                     * TODO: Fill in your client code that sends out a message.
                     */
                    try {
                        String pNo=m.get(portList[i]);
                        Log.i(TAG,"CP1: Sending "+sending);
                        outGoing.println(sending);
                        outGoing.flush();
                        try {
                            String received = inComing.readLine();
                            String[] propSplits=received.split(":");
                            sum+=Integer.parseInt(propSplits[0]);
                            proposalList.add(Integer.parseInt(propSplits[1]));
                            Log.i(TAG,"CP2: Rxd proposal from "+propSplits[0]+" for "+originalMsgSplits[1]+": "+propSplits[1]);
                        } catch (Exception e) {
                            Log.e(TAG, "CP2 Server Failure Detected: AVD"+pNo+" has failed");
                            Log.e(TAG,"Continuing...");
                            continue;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "CP1 Error");
                    }
                }

                // Detecting the failed AVD from the proposal reception part. Since for this project, only one instance failure is expected,
                // I will calculate the sum of the process numbers received and subtract it from the total 15(1+2+3+4+5)
                // This is only for debugging purposes.
                int detectingFailedNode=15-sum;
                if(detectingFailedNode==0)
                    Log.i(TAG,"CP3: All AVDs intact");
                else{
                    String failed=Integer.toString(detectingFailedNode);
                    Log.e(TAG,"CP3: Failed AVD: "+failed);
                }

                if (proposalList.size()>0) {
                    Log.i(TAG,"CP4: Array of proposals Rxd==> "+ Arrays.toString(proposalList.toArray()));
                    int maxObserved=Collections.max(proposalList);
                    Log.i(TAG,"Max ==> "+maxObserved);
                    for(int i=0;i<socketList.length;i++) {
                        DataOutputStream outgoingAgreement = new DataOutputStream(socketList[i].getOutputStream());
                        outgoingAgreement.writeUTF(originalMsgSplits[0]+":"+maxObserved+":"+originalMsgSplits[1]);
                        outgoingAgreement.flush();
                        Log.i(TAG,"CP3: Success Agreement Sent: "+originalMsgSplits[0]+":"+maxObserved+":"+originalMsgSplits[1]);
                    }
                    proposalList.clear();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


