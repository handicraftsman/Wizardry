package electroblob.wizardry.item;

import java.util.List;

import javax.annotation.Nullable;

import electroblob.wizardry.Wizardry;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemSpectralBow extends ItemBow implements IConjuredItem {

	public ItemSpectralBow(){
		super();
		this.setMaxDamage(getBaseDuration());
		this.setNoRepair();
		this.setCreativeTab(null);
		this.addPropertyOverride(new ResourceLocation("pull"), new IItemPropertyGetter()
        {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn)
            {
                if (entityIn == null)
                {
                    return 0.0F;
                }
                else
                {
                    ItemStack itemstack = entityIn.getActiveItemStack();
                    // Mojang, observe - hardcoding item references into their own classes is NOT good Java.
                    return itemstack != null && itemstack.getItem() == ItemSpectralBow.this ? (float)(stack.getMaxItemUseDuration() - entityIn.getItemInUseCount()) / 20.0F : 0.0F;
                }
            }
        });
        this.addPropertyOverride(new ResourceLocation("pulling"), new IItemPropertyGetter()
        {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn)
            {
                return entityIn != null && entityIn.isHandActive() && entityIn.getActiveItemStack() == stack ? 1.0F : 0.0F;
            }
        });
	}

	@Override
    @SideOnly(Side.CLIENT)
	// Why does this still exist? Item models deal with this now, right?
	public boolean isFull3D(){
		return true;
	}
	
	@Override
	public int getBaseDuration(){
		return 600;
	}
	
	@Override
	public int getMaxDamage(ItemStack stack){
        return this.getMaxDamageFromNBT(stack);
    }
	
	@Override
	// This method allows the code for the item's timer to be greatly simplified by damaging it directly from
	// onUpdate() and removing the workaround that involved WizardData and all sorts of crazy stuff.
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged){
		
		if(oldStack != null && newStack != null){
			// We only care about the situation where we specifically want the animation NOT to play.
			if(oldStack.getItem() == newStack.getItem() && !slotChanged
					// This code should only run on the client side, so using Minecraft is ok.
					&& !Minecraft.getMinecraft().thePlayer.isHandActive()) return false;
		}
		
		return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}
	
	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected){
		int damage = stack.getItemDamage();
		if(damage > stack.getMaxDamage()) entity.replaceItemInInventory(slot, null);
		// Can't damage it whilst in use because for some reason it causes the item use to constantly reset.
		if(!(entity instanceof EntityLivingBase) || !((EntityLivingBase)entity).isHandActive()){
			stack.setItemDamage(damage + 1);
		}
	}
	
	// The following two methods re-route the displayed durability through the proxies in order to override the pausing
	// of the item timer when the bow is being pulled.
	
	@Override
	public double getDurabilityForDisplay(ItemStack stack){
		return Wizardry.proxy.getConjuredBowDurability(stack);
	}
	
	public double getDefaultDurabilityForDisplay(ItemStack stack){
		return super.getDurabilityForDisplay(stack);
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand){

		ActionResult<ItemStack> ret = net.minecraftforge.event.ForgeEventFactory.onArrowNock(stack, world, player, hand, true);

		if(ret != null) return ret;

		player.setActiveHand(hand);

		return ActionResult.newResult(EnumActionResult.SUCCESS, stack);

	}

	@Override
    @SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack){
		return true;
	}

	@Override
	public boolean getIsRepairable(ItemStack stack, ItemStack stack2){
		return false;
	}

	@Override
	public int getItemEnchantability(){
		return 0;
	}

	// Cannot be dropped
	@Override
	public boolean onDroppedByPlayer(ItemStack item, EntityPlayer player){
		return false;
	}
	
	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase player, int count){
		// player.getItemInUseMaxCount() is named incorrectly; you only have to look at the method to see what it really
		// does.
		if(stack.getItemDamage() + player.getItemInUseMaxCount() > stack.getMaxDamage())
			player.replaceItemInInventory(player.getActiveHand() == EnumHand.MAIN_HAND ? 98 : 99, null);
	}

	@Override
	public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase entity, int timeLeft){
		// Decreases the timer by the amount it should have been decreased while the bow was in use.
		if(!world.isRemote) stack.setItemDamage(stack.getItemDamage() + (this.getMaxItemUseDuration(stack) - timeLeft));
		
		if (entity instanceof EntityPlayer){
			
			EntityPlayer entityplayer = (EntityPlayer)entity;

			int i = this.getMaxItemUseDuration(stack) - timeLeft;
			i = net.minecraftforge.event.ForgeEventFactory.onArrowLoose(stack, world, (EntityPlayer)entity, i, true);
			if (i < 0) return;

			float f = getArrowVelocity(i);

			if ((double)f >= 0.1D){

				if (!world.isRemote){
					
					ItemArrow itemarrow = (ItemArrow)Items.ARROW;
					EntityArrow entityarrow = itemarrow.createArrow(world, new ItemStack(itemarrow), entityplayer);
					entityarrow.setAim(entityplayer, entityplayer.rotationPitch, entityplayer.rotationYaw, 0.0F, f * 3.0F, 1.0F);

					if (f == 1.0F)
					{
						entityarrow.setIsCritical(true);
					}

					int j = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, stack);

					if (j > 0)
					{
						entityarrow.setDamage(entityarrow.getDamage() + (double)j * 0.5D + 0.5D);
					}

					int k = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, stack);

					if (k > 0)
					{
						entityarrow.setKnockbackStrength(k);
					}

					if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, stack) > 0)
					{
						entityarrow.setFire(100);
					}

					entityarrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;

					world.spawnEntityInWorld(entityarrow);
				}

				world.playSound((EntityPlayer)null, entityplayer.posX, entityplayer.posY, entityplayer.posZ, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.NEUTRAL, 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);

				entityplayer.addStat(StatList.getObjectUseStats(this));
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> subItems){
		subItems.add(new ItemStack(this, 1));
	}
	
}
