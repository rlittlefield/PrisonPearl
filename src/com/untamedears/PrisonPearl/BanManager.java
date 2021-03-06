package com.untamedears.PrisonPearl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

class BanManager implements Listener {
	final public static String BAN_MSG = "Banned for having too many imprisoned alts!";
	final public static String JOURNAL_BASE_NAME = "ban_journal";

	private PrisonPearlPlugin plugin_ = null;
	private Set<String> bannedPlayers_ = new TreeSet<String>();
	private File banJournal_ = null;
	private File banJournalBakOne_ = null;
	private File banJournalBakTwo_ = null;
	private String banMessage_ = BAN_MSG;
	private File baseDirectory_ = null;
	private String journalBaseName_ = JOURNAL_BASE_NAME;

	public BanManager(final PrisonPearlPlugin plugin) {
		plugin_ = plugin;
		setDirectory(plugin.getDataFolder());
	}

	public void initialize() {
		Bukkit.getPluginManager().registerEvents(this, plugin_);
		loadBanJournal();
		rotateBanJournal();
		writeBanJournal();
	}

	public void setBanMessage(String msg) {
		banMessage_ = msg;
	}

	public void setJournalBaseName(String name) {
		journalBaseName_ = name;
		initFiles();
	}

	public void setDirectory(File dir) {
		baseDirectory_ = dir;
		initFiles();
	}

	private void initFiles() {
		banJournal_ = new File(baseDirectory_, journalBaseName_ + ".dat");
		banJournalBakOne_ = new File(baseDirectory_, journalBaseName_ + ".1.bak");
		banJournalBakTwo_ = new File(baseDirectory_, journalBaseName_ + ".2.bak");
	}

	public boolean isBanned(String playerName) {
		return bannedPlayers_.contains(playerName.toLowerCase());
	}

    public Set<String> listBannedPlayers() {
		return bannedPlayers_;
    }

	public void ban(String playerName) {
		setBanState(playerName, true);
	}

	public void pardon(String playerName) {
		setBanState(playerName, false);
	}

	public void setBanState(String playerName, boolean isBanned) {
		playerName = playerName.toLowerCase();
		final boolean currentlyBanned = bannedPlayers_.contains(playerName);
		if (isBanned != currentlyBanned) {
			recordToBanJournal(playerName, isBanned);
			if (isBanned) {
				bannedPlayers_.add(playerName);
			} else {
				bannedPlayers_.remove(playerName);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		final String playerName = event.getName().toLowerCase();
		if (!bannedPlayers_.contains(playerName)) {
			return;
		}
		final OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
		if (offline != null) {
			if (offline.isBanned() || offline.isOp()) {
				return;
			}
		}
		event.disallow(
			AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage_);
	}

	private void writeBanJournal() {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(banJournal_, true);
			for (String playerName : bannedPlayers_) {
				playerName = playerName.toLowerCase();
				recordToBanJournal(fos, playerName, true);
			}
		} catch (IOException e) {
			PrisonPearlPlugin.info("Failed to append ban journal: " + e.toString());
			e.printStackTrace();
		} finally {
			try {
				if (fos != null) {
					fos.close();
					fos = null;
				}
			} catch (IOException ie) {
				PrisonPearlPlugin.info("Failed to close ban journal: " + ie.toString());
			}
		}
	}

	private void recordToBanJournal(String name, boolean isBanned) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(banJournal_, true);
			recordToBanJournal(fos, name, isBanned);
		} catch (IOException e) {
			PrisonPearlPlugin.info("Failed to append ban journal: " + e.toString());
			e.printStackTrace();
		} finally {
			try {
				if (fos != null) {
					fos.close();
					fos = null;
				}
			} catch (IOException ie) {
				PrisonPearlPlugin.info("Failed to close ban journal: " + ie.toString());
			}
		}
	}

	private void recordToBanJournal(FileOutputStream fos, String name, boolean isBanned) {
		name = name.toLowerCase();
		final String ban_record = String.format(
			"%s|%s\n", name, isBanned ? "ban" : "free");
		try {
			fos.write(ban_record.getBytes(Charset.forName("UTF-8")));
		} catch (IOException e) {
			PrisonPearlPlugin.info("Failed to append ban journal: " + ban_record);
			e.printStackTrace();
		}
	}

	private void loadBanJournal() {
		// Only keep bans on load
		if (bannedPlayers_ == null || bannedPlayers_.size() > 0) {
			bannedPlayers_ = new TreeSet<String>();
		}
		for (Map.Entry<String, Boolean> entry : readBanJournal(false).entrySet()) {
			final Boolean isBanned = entry.getValue();
			if (isBanned == null || !isBanned) {
				continue;
			}
			final String playerName = entry.getKey().toLowerCase();
			bannedPlayers_.add(playerName);
		}
	}

	public Map<String, Boolean> readBanJournal(boolean openPreviousJournal) {
		// The journal is append only so the last line for a player wins. This
		//  just reads all players and updates the Map each line, resulting in
		//  the same.
		Map<String, Boolean> banState = new TreeMap<String, Boolean>();
		File activeJournal = openPreviousJournal ? banJournalBakOne_ : banJournal_;
		if (!activeJournal.exists()) {
			try {
				activeJournal.createNewFile();
			} catch (IOException e) {
				PrisonPearlPlugin.info("Unable to create ban journal: " + activeJournal.getPath());
				e.printStackTrace();
			}
			return banState;
		}
		// Load the journal
		BufferedReader br = null;
		try {
			br = new BufferedReader(
				new InputStreamReader(
					new FileInputStream(activeJournal),
					Charset.forName("UTF-8")));
			while (true) {
				String line = br.readLine();
				if (line == null) {
					break;
				}
				if (line.length() <= 0) {
					continue;
				}
				String[] parts = line.split("\\|");
				if (parts.length < 2) {
					PrisonPearlPlugin.info("Malformed line: " + line);
					continue;
				}
				final String playerName = parts[0].toLowerCase();
				final boolean isBanned = "ban".equalsIgnoreCase(parts[1]);
				banState.put(playerName, isBanned);
			}
		} catch (IOException e) {
			PrisonPearlPlugin.info("Failed to read ban journal");
			e.printStackTrace();
		} finally {
			try {
				if (br != null) {
					br.close();
					br = null;
				}
			} catch (IOException ie) {
				PrisonPearlPlugin.info("Failed to close ban journal: " + ie.toString());
			}
		}
		return banState;
	}

	private void rotateBanJournal() {
		try {
			banJournalBakTwo_.delete();
			banJournalBakOne_.renameTo(banJournalBakTwo_);
			banJournal_.renameTo(banJournalBakOne_);
			banJournal_.createNewFile();
		} catch (Exception e) {
			PrisonPearlPlugin.info("Failed to rotate ban journal");
			e.printStackTrace();
		}
	}
}
