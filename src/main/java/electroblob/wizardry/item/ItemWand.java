package electroblob.wizardry.item;

import java.util.List;

import electroblob.wizardry.SpellGlyphData;
import electroblob.wizardry.WizardData;
import electroblob.wizardry.Wizardry;
import electroblob.wizardry.constants.Constants;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.event.SpellCastEvent.Source;
import electroblob.wizardry.packet.PacketCastSpell;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.registry.WizardryAchievements;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.registry.WizardryTabs;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import electroblob.wizardry.util.WizardryUtilities;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** This class is (literally) where the magic happens! All wand types are single instances of this class. There's a lot
 * of quite hard-to-read code in here, but unfortunately there's not much I can do about that. For this reason, I have
 * written the {@link WandHelper} class.<i> I strongly recommend you use it for interacting with wand items wherever
 * possible.</i>
 * <p>
 * It's unikely that anything in this class will be of much use externally, but should you wish to use it for whatever
 * reason (perhaps if you extend it), it works as follows:
 * <p>
 * - onItemRightClick is where non-continuous spells are cast, and it sets the item in use for continuous spells<br>
 * - onUsingTick does the casting for continuous spells<br>
 * - onUpdate deals with the cooldowns for the spells
 * @since Wizardry 1.0 */
public class ItemWand extends Item {

	public Tier tier;
	public Element element;

	public ItemWand(Tier tier, Element element) {
		super();
		setMaxStackSize(1);
		if(element == null || tier == Tier.BASIC){
			setCreativeTab(WizardryTabs.WIZARDRY);
		}
		this.tier = tier;
		this.element = element;
		this.setMaxDamage(this.tier.maxCharge);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isFull3D(){
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public net.minecraft.client.gui.FontRenderer getFontRenderer(ItemStack stack){
		return Wizardry.proxy.getFontRenderer(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item parItem, CreativeTabs parTab, List<ItemStack> parListSubItems){
		parListSubItems.add(new ItemStack(this, 1));
	}

	// Max damage is modifiable with upgrades.
	@Override
	public int getMaxDamage(ItemStack itemstack){
		// + 0.5f corrects small float errors rounding down
		return (int)(super.getMaxDamage(itemstack)*(1.0f + Constants.STORAGE_INCREASE_PER_LEVEL * WandHelper.getUpgradeLevel(itemstack, WizardryItems.storage_upgrade)) + 0.5f);
	}

	@Override
	public void onCreated(ItemStack stack, World par2World, EntityPlayer par3EntityPlayer){
		par3EntityPlayer.addStat(WizardryAchievements.arcane_initiate);
	}

	@Override
	public void onUpdate(ItemStack itemstack, World world, Entity entity, int slot, boolean isHeld){

		WandHelper.decrementCooldowns(itemstack);

		// Decrements wand damage (increases mana) every 1.5 seconds if it has a condenser upgrade
		if(!world.isRemote && itemstack.isItemDamaged() && world.getWorldTime() % Constants.CONDENSER_TICK_INTERVAL == 0){
			// If the upgrade level is 0, this does nothing anyway.
			itemstack.setItemDamage(itemstack.getItemDamage() - WandHelper.getUpgradeLevel(itemstack, WizardryItems.condenser_upgrade));
		}

		if(entity instanceof EntityPlayer && this.element != null && this.element != Element.MAGIC){
			// As it stands, this will trigger every tick. Not ideal, but I can't find a way to detect if a player
			// has a certain achievement.
			// EDIT: There is a way to check, using StatFileWriter#hasAchievementUnlocked, but this ends up calling the same
			// thing as addStat anyway, meaning there's no point and it's probably not much of a problem anyway.
			((EntityPlayer)entity).addStat(WizardryAchievements.elemental);
		}
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged){

		// This method does some VERY strange things! Despite its name, it also seems to affect the updating of NBT...

		if(oldStack != null || newStack != null){
			// We only care about the situation where we specifically want the animation NOT to play.
			if(oldStack.getItem() == newStack.getItem() && !slotChanged
					&& oldStack.getItem() instanceof ItemWand && newStack.getItem() instanceof ItemWand
					&& WandHelper.getCurrentSpell(oldStack) == WandHelper.getCurrentSpell(newStack)) return false;
		}

		return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	@Override
	public EnumAction getItemUseAction(ItemStack itemstack){
		return WandHelper.getCurrentSpell(itemstack).action;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack){
		return 72000;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void addInformation(ItemStack itemstack, EntityPlayer player, List<String> text, boolean advanced){

		// +0.5f is necessary due to the error in the way floats are calculated.
		if(element != null) text.add("\u00A78" + net.minecraft.client.resources.I18n.format("item.wizardry:wand.buff", (int)((tier.level+1)*Constants.DAMAGE_INCREASE_PER_TIER*100 + 0.5f) + "%", element.getDisplayName()));

		Spell spell = WandHelper.getCurrentSpell(itemstack);

		boolean discovered = true;
		if(Wizardry.settings.discoveryMode && !player.capabilities.isCreativeMode && WizardData.get(player) != null
				&& !WizardData.get(player).hasSpellBeenDiscovered(spell)){
			discovered = false;
		}

		text.add("\u00A77" + net.minecraft.client.resources.I18n.format("item.wizardry:wand.spell", discovered ? "\u00A77" + spell.getDisplayNameWithFormatting() : "#\u00A79" + SpellGlyphData.getGlyphName(spell, player.worldObj)));

		text.add("\u00A79" + net.minecraft.client.resources.I18n.format("item.wizardry:wand.mana", (this.getMaxDamage(itemstack) - this.getDamage(itemstack)), this.getMaxDamage(itemstack)));
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack){
		return (this.element == null ? "" : this.element.getFormattingCode()) + super.getItemStackDisplayName(stack);
	}
	
	// Continuous spells use the onUsingItemTick method instead of this one.
	/* An important thing to note about this method: it is only called on the server and the client of the player
	 * holding the item (I call this client-inconsistency). This means if you spawn particles here they will not show up
	 * on other players' screens. Instead, this must be done via packets. */
	@Override
	public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand){

		// Alternate right-click function; overrides spell casting.
		if(this.selectMinionTarget(player, world)) return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
		
		Spell spell = WandHelper.getCurrentSpell(stack);
		SpellModifiers modifiers = this.calculateModifiers(stack, spell);
		
		// If anything stops the spell working at this point, nothing else happens.
		if(MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Pre(player, spell, modifiers, Source.WAND))){
			return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
		}

		// This is here to start the inUse thing, otherwise the onUsingTick method will not fire.
		if(spell.isContinuous && !player.isHandActive()){
			player.setActiveHand(hand);
			// Probably ought to be here. (Does it succeed though?)
			return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
		}

		// Conditions for the spell to be attempted. The tier check is a failsafe; it should never be false unless the
		// NBT is modified directly.
		if(!spell.isContinuous && spell.tier.level <= this.tier.level
				// Checks that the wand has enough mana to cast the spell
				&& spell.cost <= (stack.getMaxDamage() - stack.getItemDamage())
				// Checks that the spell is not in cooldown or that the player is in creative mode
				&& (WandHelper.getCurrentCooldown(stack) == 0 || player.capabilities.isCreativeMode)){

			// If the spell does not require a packet, the code is run in the old client-inconsistent way, since this
			// means that swingItem() doesn't need packets in order to work, improving performance.
			if(!world.isRemote){

				if(spell.cast(world, player, hand, 0, modifiers)){
					
					MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(player, spell, modifiers, Source.WAND));

					// = Packets =
					if(spell.doesSpellRequirePacket()){
						// Sends a packet to all players in dimension to tell them to spawn particles.
						// Only sent if the spell succeeded, because if the spell failed, you wouldn't
						// need to spawn any particles!
						IMessage msg = new PacketCastSpell.Message(player.getEntityId(), hand, spell.id(), modifiers);
						WizardryPacketHandler.net.sendToDimension(msg, world.provider.getDimension());
					}

					player.setActiveHand(hand);

					// = Cooldown =
					// Spells only have a cooldown in survival
					if(!player.capabilities.isCreativeMode){

						float cooldownMultiplier = 1.0f - WandHelper.getUpgradeLevel(stack, WizardryItems.cooldown_upgrade)*Constants.COOLDOWN_REDUCTION_PER_LEVEL;

						if(player.isPotionActive(WizardryPotions.font_of_mana)){
							// Dividing by this rather than setting it takes upgrades and font of mana into account simultaneously
							cooldownMultiplier /= 2 + player.getActivePotionEffect(WizardryPotions.font_of_mana).getAmplifier();
						}

						WandHelper.setCurrentCooldown(stack, (int)(spell.cooldown * cooldownMultiplier));
					}

					// = Mana cost =
					// The spell costs 20% less for every armour piece of the matching element.
					int armourPieces = getMatchingArmourCount(player, spell);

					stack.damageItem((int)(spell.cost * (1.0f - armourPieces*Constants.COST_REDUCTION_PER_ARMOUR)), player);

					return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
				}

			}else if(!spell.doesSpellRequirePacket()){
				// Client-inconsistent spell casting. This code only runs client-side.
				if(spell.cast(world, player, hand, 0, modifiers)){
					// This is all that needs to happen, because everything above works fine on just the server side.
					MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(player, spell, modifiers, Source.WAND));
					return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
				}
			}
		}

		return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
	}

	// For continuous spells. The count argument actually decrements by 1 each tick.
	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase user, int count){

		if(user instanceof EntityPlayer){

			EntityPlayer player = (EntityPlayer)user;
			
			Spell spell = WandHelper.getCurrentSpell(stack);
			SpellModifiers modifiers = this.calculateModifiers(stack, spell);
			int castingTick = stack.getMaxItemUseDuration() - count;
			
			if(MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Tick(player, spell, modifiers, Source.WAND, castingTick))) return;

			// Continuous spells (these must check if they can be cast each tick since the mana changes)
			if(spell.isContinuous && spell.tier.level <= this.tier.level
					&& spell.cost/5 <= (stack.getMaxDamage() - stack.getItemDamage())){

				if(spell.cast(player.worldObj, player, player.getActiveHand(), castingTick, modifiers)){

					if(castingTick == 0) MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(player, spell, modifiers, Source.WAND));

					// = Mana cost =
					// Divides the mana cost over a second appropriately; since damage is an integer it cannot
					// just be divided by 20.
					// Now does five times per second regardless of the spell cost, but each time it does 1/5 of the
					// cost per second.
					int tickNumber = (count % 20) + 1;
					// Tests if the tick counter is a multiple of 4 plus 1, i.e. is true when tickNumber = 1, 5, 9, 13 or 17.
					// Made a slight adjustment since the counter starts on 1 and not 4.
					if(tickNumber % 4 == 1){

						int armourPieces = getMatchingArmourCount(player, spell);

						switch(armourPieces){

						case 0: stack.damageItem(spell.cost/5, player);
						break;

						case 1: if(tickNumber != 17) stack.damageItem(spell.cost/5, player);
						break;

						case 2: if(tickNumber != 9 && tickNumber != 17) stack.damageItem(spell.cost/5, player);
						break;

						case 3: if(tickNumber != 5 && tickNumber != 13 && tickNumber != 17) stack.damageItem(spell.cost/5, player);
						break;

						case 4: if(tickNumber == 1) stack.damageItem(spell.cost/5, player);
						break;

						}
					}
				}
			}
		}
	}

	@Override
	public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase entity, EnumHand hand){

		if(player.isSneaking() && entity instanceof EntityPlayer && WizardData.get(player) != null){
			// This is one of those "the method doing the work looks as if it's just returning a value" situations.
			// ... I know, right?! I feel very programmer-y. But it's not too confusing here, and it looks neat.
			String string = WizardData.get(player).toggleAlly((EntityPlayer)entity) ? "item.wizardry:wand.addally" : "item.wizardry:wand.removeally";
			if(!player.worldObj.isRemote) player.addChatMessage(new TextComponentTranslation(string, entity.getName()));
			return true;
		}

		return false;
	}

	/** Returns a SpellModifiers object with the appropriate modifiers applied for the given ItemStack and Spell. */
	protected SpellModifiers calculateModifiers(ItemStack stack, Spell spell){

		SpellModifiers modifiers = new SpellModifiers();

		// Now we only need to add multipliers if they are not 1.
		int level = WandHelper.getUpgradeLevel(stack, WizardryItems.range_upgrade);
		if(level > 0) modifiers.set(WizardryItems.range_upgrade, 1.0f + level * Constants.RANGE_INCREASE_PER_LEVEL, true);

		level = WandHelper.getUpgradeLevel(stack, WizardryItems.duration_upgrade);
		if(level > 0) modifiers.set(WizardryItems.duration_upgrade, 1.0f + level * Constants.DURATION_INCREASE_PER_LEVEL, false);

		level = WandHelper.getUpgradeLevel(stack, WizardryItems.blast_upgrade);
		if(level > 0) modifiers.set(WizardryItems.blast_upgrade, 1.0f + level * Constants.BLAST_RADIUS_INCREASE_PER_LEVEL, true);

		// I would have liked to have made potion effects increase in strength according to the damage multiplier,
		// but the amplifier level is too discrete to make this work. For example, wither 3 for 10 seconds will kill a
		// normal mob on full 20 health, but wither 2 for the same duration only deals about 6 hearts of damage in total.
		if(this.element == spell.element){
			modifiers.set(SpellModifiers.DAMAGE, 1.0f + (this.tier.level + 1) * Constants.DAMAGE_INCREASE_PER_TIER, true);
		}

		return modifiers;
	}

	/** Counts the number of armour pieces the given player is wearing that match the given spell's element. */
	private int getMatchingArmourCount(EntityPlayer player, Spell spell){

		int armourPieces = 0;

		for(EntityEquipmentSlot slot : WizardryUtilities.ARMOUR_SLOTS){

			ItemStack armour = player.getItemStackFromSlot(slot);

			if(armour != null && armour.getItem() instanceof ItemWizardArmour
					&& ((ItemWizardArmour)armour.getItem()).element == spell.element) armourPieces++;
		}

		return armourPieces;
	}

	private boolean selectMinionTarget(EntityPlayer player, World world){

		RayTraceResult rayTrace = WizardryUtilities.standardEntityRayTrace(world, player, 16);

		if(rayTrace != null && rayTrace.entityHit instanceof EntityLivingBase){

			EntityLivingBase entity = (EntityLivingBase)rayTrace.entityHit;

			// Sets the selected minion's target to the right-clicked entity
			if(player.isSneaking() && WizardData.get(player) != null && WizardData.get(player).selectedMinion != null){

				ISummonedCreature minion = WizardData.get(player).selectedMinion.get();

				if(minion instanceof EntityLiving && minion != entity){
					// There is now only the new AI! (which greatly improves things)
					((EntityLiving)minion).setAttackTarget(entity);
					// Deselects the selected minion
					WizardData.get(player).selectedMinion = null;
					return true;
				}
			}
		}

		return false;
	}
}
