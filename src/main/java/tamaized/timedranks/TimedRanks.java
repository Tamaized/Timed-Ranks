package tamaized.timedranks;

import com.feed_the_beast.ftblib.events.ServerReloadEvent;
import com.feed_the_beast.ftblib.lib.util.misc.Node;
import com.feed_the_beast.ftbutilities.FTBUtilities;
import com.feed_the_beast.ftbutilities.ranks.Rank;
import com.feed_the_beast.ftbutilities.ranks.Ranks;
import com.google.gson.JsonElement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.StatList;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.server.permission.context.PlayerContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber
@Mod(modid = TimedRanks.MODID, dependencies = "required-after:" + FTBUtilities.MOD_ID + "@[" + FTBUtilities.VERSION + ",)")
public class TimedRanks {

	public static final String MODID = "timedranks";

	public static final String ID_TIMER = MODID + ".timer";
	public static final String ID_OVERRIDE = MODID + ".override";

	final static List<LongPair<Rank>> ranks = new ArrayList<>();

	@SubscribeEvent
	public static void tick(TickEvent.PlayerTickEvent e) {
		if (e.phase != TickEvent.Phase.START || e.player.world == null || e.player.world.isRemote || !(e.player instanceof EntityPlayerMP) || e.player.ticksExisted <= 0 || e.player.ticksExisted % (20 * 30) != 0)
			return;
		Rank rank = Ranks.INSTANCE.getRank(e.player.getServer(), e.player.getGameProfile(), new PlayerContext(e.player));
		JsonElement timer = rank.getConfigRaw(Node.get(ID_TIMER));
		JsonElement override = rank.getConfigRaw(Node.get(ID_OVERRIDE));
		if ((Ranks.INSTANCE.getDefaultPlayerRank().equals(rank) && (override.isJsonNull() || override.getAsBoolean())) ||

				(!override.isJsonNull() && override.getAsBoolean()) ||

				(!timer.isJsonNull() && (override.isJsonNull() || override.getAsBoolean()))) {
			long ticks = ((EntityPlayerMP) e.player).getStatFile().readStat(StatList.PLAY_ONE_MINUTE);
			for (LongPair<Rank> next : ranks)
				if (next.value <= ticks) {
					if (!rank.equals(next.entry)) {
						Ranks.INSTANCE.setRank(e.player.getUniqueID(), next.entry);
						if (e.player.getServer() != null)
							e.player.getServer().getPlayerList().sendMessage(new TextComponentTranslation(MODID + ".message.upgrade", e.player.getDisplayName(), next.entry.getDisplayName()));
					}
					break;
				}
		}
	}

	@SubscribeEvent
	public static void reload(ServerReloadEvent e) {
		ranks.clear();
		Ranks.INSTANCE.ranks.values().forEach(rank -> {
			JsonElement element = rank.getConfigRaw(Node.get(ID_TIMER));
			int val = element.isJsonNull() ? -1 : element.getAsInt();
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
