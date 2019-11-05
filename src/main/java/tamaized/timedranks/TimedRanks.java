package tamaized.timedranks;

import com.feed_the_beast.ftblib.events.ServerReloadEvent;
import com.feed_the_beast.ftblib.lib.util.ServerUtils;
import com.feed_the_beast.ftblib.lib.util.StringUtils;
import com.feed_the_beast.ftblib.lib.util.misc.Node;
import com.feed_the_beast.ftbutilities.FTBUtilities;
import com.feed_the_beast.ftbutilities.ranks.Rank;
import com.feed_the_beast.ftbutilities.ranks.Ranks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.stats.StatList;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
@Mod(modid = TimedRanks.MODID, acceptableRemoteVersions = "*", dependencies = "required-after:" + FTBUtilities.MOD_ID)
public class TimedRanks {

	public static final String MODID = "timedranks";

	public static final String ID_TIMER = MODID + ".timer";
	public static final String ID_OVERRIDE = MODID + ".override";

	final static List<LongPair<Rank>> ranks = new ArrayList<>();
	final static List<UUID> HASMOD = new ArrayList<>();

	@SubscribeEvent
	public static void tick(TickEvent.PlayerTickEvent e) {
		if (e.phase != TickEvent.Phase.START || e.player.world == null || e.player.world.isRemote || !(e.player instanceof EntityPlayerMP) || e.player.ticksExisted <= 0 || e.player.ticksExisted % (20 * 30) != 0)
			return;
		if (e.player.getServer() == null)
			return;
		Rank rank = Ranks.INSTANCE.getPlayerRank(e.player);
		boolean timerIsNull = rank.getConfig(Node.get(ID_TIMER)).isEmpty();
		String override = rank.getConfig(Node.get(ID_OVERRIDE));
		if (rank.getId().equals(StringUtils.fromUUID(e.player.getUniqueID())) && timerIsNull && override.isEmpty()) {
			rank = ServerUtils.isOP(e.player.getServer(), e.player.getGameProfile()) ? Ranks.INSTANCE.getDefaultOPRank() : Ranks.INSTANCE.getDefaultPlayerRank();
			timerIsNull = rank.getConfig(Node.get(ID_TIMER)).isEmpty();
			override = rank.getConfig(Node.get(ID_OVERRIDE));
		}
		if ((rank.isDefaultPlayerRank() && (override.isEmpty() || Boolean.parseBoolean(override))) ||

				(!override.isEmpty() && Boolean.parseBoolean(override)) ||

				(timerIsNull && (override.isEmpty() || Boolean.parseBoolean(override)))) {
			long ticks = ((EntityPlayerMP) e.player).getStatFile().readStat(StatList.PLAY_ONE_MINUTE);
			for (LongPair<Rank> next : ranks)
				if (next.value <= ticks) {
					if (!rank.equals(next.entry)) {
						Ranks.INSTANCE.getPlayerRank(e.player).addParent(next.entry);
						if (e.player.getServer() != null) {
							ITextComponent component = new TextComponentTranslation(MODID + ".message.upgrade", e.player.getDisplayName(), next.entry.getDisplayName());
							e.player.getServer().sendMessage(component);
							Packet<?> packetTranslated = new SPacketChat(component, ChatType.SYSTEM);
							Packet<?> packetNoMod = new SPacketChat(new TextComponentTranslation("%s has earned the rank: %s", e.player.getDisplayName(), next.entry.getDisplayName()), ChatType.SYSTEM);
							for (int i = 0; i < e.player.getServer().getPlayerList().getPlayers().size(); ++i) {
								(e.player.getServer().getPlayerList().getPlayers().get(i)).connection.sendPacket(HASMOD.contains(e.player.getUniqueID()) ? packetTranslated : packetNoMod);
							}
						}
					}
					break;
				}
		}
	}

	@SubscribeEvent
	public static void login(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.player instanceof EntityPlayerMP) {
			if (NetworkDispatcher.get(((EntityPlayerMP) event.player).connection.netManager).getModList().containsKey(MODID))
				HASMOD.add(event.player.getUniqueID());
		}
	}

	@SubscribeEvent
	public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
		HASMOD.remove(event.player.getUniqueID());
	}

	@SubscribeEvent
	public static void reload(ServerReloadEvent e) {
		ranks.clear();
		Ranks.INSTANCE.ranks.values().forEach(rank -> {
			String value = rank.getConfig(Node.get(ID_TIMER));
			int val = -1;
			try {
				val = value.isEmpty() ? -1 : Integer.parseInt(value);
			} catch (NumberFormatException ex) {
				// NO-OP
			}
			if (val > 0)
				ranks.add(new LongPair<>(rank, val));
		});
		ranks.sort(Comparator.<LongPair<Rank>>comparingLong(p -> p.value).reversed());
	}

	private static class LongPair<T> {
		final T entry;
		final long value;

		public LongPair(T entry, long value) {
			this.entry = entry;
			this.value = value;
		}
	}

}
