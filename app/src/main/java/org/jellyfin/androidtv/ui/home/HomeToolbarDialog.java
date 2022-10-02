package org.jellyfin.androidtv.ui.home;

import static org.koin.java.KoinJavaComponent.inject;

import android.app.Activity;
import android.content.Context;

import androidx.core.util.Pair;

import org.jellyfin.androidtv.auth.model.Server;
import org.jellyfin.androidtv.auth.repository.ServerRepository;
import org.jellyfin.androidtv.ui.shared.DialogBlockingAsyncTask;
import org.jellyfin.androidtv.util.WakeOnLanUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import kotlin.Lazy;
import timber.log.Timber;

class HomeToolbarDialog extends DialogBlockingAsyncTask<Server, Void, Boolean> {

    private final Lazy<ServerRepository> serverRepository = inject(ServerRepository.class);

    public HomeToolbarDialog(Context context) {
        super(context);

        pending_title_id = "PENDING TITLE";
        pending_msg_id = "PENDING MESSAGE";
        error_title_id = "Error";
        error_msg_id = "ERROR MESSAGE";
        success_title_id = "Success";
        success_msg_id = "SUCCESS MESSAGE";


        success_button_text_id = "OK";
        error_button_text_id = "OK";

    }

    private Boolean error(String format, Object... args) {

        this.error_msg_id = String.format(format, args);
        Timber.e(this.error_msg_id);
        cancel(true);
        return false;
    }

    private Boolean success(String format, Object... args) {

        this.success_msg_id = String.format(format, args);
        Timber.d(this.success_msg_id);
        return true;
    }

    private void pending(String title, String msgFormat, Object... args) {
        this.pending_title_id = title;
        this.pending_msg_id = String.format(msgFormat, args);

        ((Activity)mContext).runOnUiThread(() -> {

            this.currentDialog.setTitle(this.pending_title_id);
            this.currentDialog.setMessage(this.pending_msg_id);
            Timber.d(this.pending_msg_id);
        });
    }

    @Override
    protected Boolean doInBackground(Server... params) {


        try {
            ServerRepository repo = this.serverRepository.getValue();


            Timber.d("Server Repo : %s", repo);
            Timber.d("Server Repo Stored Servers : %s", repo.getStoredServers());
            Timber.d("Server Repo Stored Servers Value : %s", repo.getStoredServers().getValue());
            List<Server> servers = repo.getStoredServers().getValue();

            if (servers.isEmpty()) {
                return error("No servers in list");
            }

            Server server = params[0];
            if (server == null) return error("Unknown error occurred");


            pending("Checking state", "Checking if server %s is currently awake", server.getName());
            String serverIp = server.getAddress().replaceFirst("http://", "").replaceFirst("\\:\\d*", "");
            InetAddress serverAddress = InetAddress.getByName(serverIp);
            if (serverAddress.isReachable(3000)) {
                return error("Server %s at %s is already reachable", server.getName(), serverIp);
            }

            if (!serverAddress.isSiteLocalAddress()) {
                return error("Can't wake up server %s at %s because it's not on local network", server.getName(), serverIp);
            }

            pending("Retrieving server info", "Looking for valid network addresses...", server.getName());
            Pair<String, String> ifName_macAddr = WakeOnLanUtil.getMacAddressOf(serverAddress);
            Timber.d("Mac address : %s/%s", ifName_macAddr.first, ifName_macAddr.second);
            Map<String, InetAddress> broadcastAddresses = WakeOnLanUtil.getBroadcastAddresses(serverAddress);
            broadcastAddresses.forEach((ifName, broadcast) -> {
                Timber.d("Broadcast found : if=%s, broadcast=%s", ifName, broadcast);
            });

            InetAddress broadcastAddress = broadcastAddresses.get(ifName_macAddr.first);
            if (broadcastAddress == null) {
                return error("No MAC address found for server %s", server.getName());
            }

            Timber.d("Found MAC %s on IF %s with BROADCAST %s for remote target IP %s", ifName_macAddr.second, ifName_macAddr.first, broadcastAddress, serverIp);

            pending("Waking up server", "Sending WoL packet to %s", server.getName());
            boolean result = WakeOnLanUtil.wake(mContext, broadcastAddress.getHostAddress(), ifName_macAddr.second);
            if (!result) return error("Failed to wake up server");

            pending("Checking state", "Checking if server %s is now awake", server.getName());
            if (serverAddress.isReachable(10000)) {
                return success("Server %s at %s is now reachable", server.getName(), serverIp);
            } else {
                return error("Server %s didn't respond to WoL", server.getName());
            }

        } catch (IndexOutOfBoundsException | IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}
