package electroblob.wizardry.item;

import java.util.List;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.constants.Constants;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.registry.WizardryTabs;
import electroblob.wizardry.spell.Petrify;
import electroblob.wizardry.util.WizardryUtilities;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemWizardArmour extends ItemArmor implements ISpecialArmor {

	public Element element;

	public ItemWizardArmour(ArmorMaterial material, int renderIndex, EntityEquipmentSlot armourType, Element element) {
		super(material, renderIndex, armourType);
		this.element = element;
		this.setCreativeTab(WizardryTabs.WIZARDRY);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced){
		
		if(stack.hasTagCompound() && stack.getTagCompound().getBoolean("legendary")) tooltip.add("\u00A7d" + net.minecraft.client.resources.I18n.format("item.wizardry:wizard_armour.legendary"));
		if(element != null) tooltip.add("\u00A78" + net.minecraft.client.resources.I18n.format("item.wizardry:wizard_armour.buff", (int)(Constants.COST_REDUCTION_PER_ARMOUR*100) + "%", element.getDisplayName()));
		tooltip.add("\u00A79" + net.minecraft.client.resources.I18n.format("item.wizardry:wizard_armour.mana", (this.getMaxDamage(stack) - this.getDamage(stack)), this.getMaxDamage(stack)));
	}
	
	@Override
	public String getItemStackDisplayName(ItemStack stack){
        return ((this.element == null ? "" : this.element.getFormattingCode()) + super.getItemStackDisplayName(stack));
    }
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack){
		return stack.hasTagCompound() && stack.getTagCompound().getBoolean("legendary");
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public net.minecraft.client.model.ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, EntityEquipmentSlot armourSlot, net.minecraft.client.model.ModelBiped _default){
		
		net.minecraft.client.model.ModelBiped model = Wizardry.proxy.getWizardArmourModel();
		
		// Legs use modelBiped
		if(armourSlot == EntityEquipmentSlot.LEGS) return null;
		
		if(model != null){
			
			model.bipedHead.showModel = armourSlot == EntityEquipmentSlot.HEAD;
			model.bipedHeadwear.showModel = false;
			model.bipedBody.showModel = armourSlot == EntityEquipmentSlot.CHEST || armourSlot == EntityEquipmentSlot.LEGS;
			model.bipedRightArm.showModel = armourSlot == EntityEquipmentSlot.CHEST;
			model.bipedLeftArm.showModel = armourSlot == EntityEquipmentSlot.CHEST;
			model.bipedRightLeg.showModel = armourSlot == EntityEquipmentSlot.LEGS || armourSlot == EntityEquipmentSlot.FEET;
			model.bipedLeftLeg.showModel = armourSlot == EntityEquipmentSlot.LEGS || armourSlot == EntityEquipmentSlot.FEET;
			
			model.isSneak = entityLiving.isSneaking();
			model.isRiding = entityLiving.isRiding();
			model.isChild = entityLiving.isChild();
			
			boolean leftHanded = entityLiving.getPrimaryHand() == EnumHandSide.LEFT;
			
			ItemStack itemstackR = leftHanded ? entityLiving.getHeldItemOffhand() : entityLiving.getHeldItemMainhand();
			ItemStack itemstackL = leftHanded ? entityLiving.getHeldItemMainhand() : entityLiving.getHeldItemOffhand();

			if (itemstackR != null)
            {
                model.rightArmPose = net.minecraft.client.model.ModelBiped.ArmPose.ITEM;

                if (entityLiving.getItemInUseCount() > 0)
                {
                    EnumAction enumaction = itemstackR.getItemUseAction();

                    if (enumaction == EnumAction.BLOCK)
                    {
                    	model.rightArmPose = net.minecraft.client.model.ModelBiped.ArmPose.BLOCK;
                    }
                    else if (enumaction == EnumAction.BOW)
                    {
                    	model.rightArmPose = net.minecraft.client.model.ModelBiped.ArmPose.BOW_AND_ARROW;
                    }
                }
            }

            if (itemstackL != null)
            {
            	model.leftArmPose = net.minecraft.client.model.ModelBiped.ArmPose.ITEM;

                if (entityLiving.getItemInUseCount() > 0)
                {
                    EnumAction enumaction1 = itemstackL.getItemUseAction();

                    if (enumaction1 == EnumAction.BLOCK)
                    {
                    	model.leftArmPose = net.minecraft.client.model.ModelBiped.ArmPose.BLOCK;
                    }
                    else if (enumaction1 == EnumAction.BOW)
                    {
                    	model.leftArmPose = net.minecraft.client.model.ModelBiped.ArmPose.BOW_AND_ARROW;
                    }
                }
            }
		}
		
        return model;
    }
	
	@Override
	public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {

		// Returns a completely transparent texture if the player is invisible. This is such an annoyingly easy
		// fix, considering how long I spent trying to do this before - a bit of lateral thinking was all it took.
		// Do note however that a texture pack could override this.
		if(entity instanceof EntityLivingBase && ((EntityLivingBase)entity).isInvisible()
				&& !entity.getEntityData().getBoolean(Petrify.NBT_KEY)) return "wizardry:textures/armour/invisible_armour.png";
			
		if(slot == EntityEquipmentSlot.LEGS) return this.element == null ? "wizardry:textures/armour/wizard_armour_legs.png" :
        	"wizardry:textures/armour/wizard_armour_" + this.element.getUnlocalisedName() + "_legs.png";
		
        return this.element == null ? "wizardry:textures/armour/wizard_armour.png" :
        	"wizardry:textures/armour/wizard_armour_" + this.element.getUnlocalisedName() + ".png";
	}
	
	@Override
	public boolean getIsRepairable(ItemStack stack, ItemStack par2ItemStack)
    {
        return false;
    }

	@Override
	public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage, int slotIndex){
		
		EntityEquipmentSlot slot = getArmorSlotFromIndex(slotIndex);
		
		if (!source.isUnblockable() && armor.getItemDamage() < armor.getMaxDamage()){
			if(armor.hasTagCompound() && armor.getTagCompound().getBoolean("legendary")){
				// Legendary armour gives full 10 shields, like diamond.
				return new ArmorProperties(0, ArmorMaterial.DIAMOND.getDamageReductionAmount(slot) / 25D, armor.getMaxDamage() + 1 - armor.getItemDamage());
			}else{
				return new ArmorProperties(0, this.damageReduceAmount / 25D, armor.getMaxDamage() + 1 - armor.getItemDamage());
			}
        }else{
        	return new ArmorProperties(0, 0, 0);
        }
	}

	@Override
	public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slotIndex) {
		
		EntityEquipmentSlot slot = getArmorSlotFromIndex(slotIndex);
		
		if(armor.getItemDamage() < armor.getMaxDamage()){
			if(armor.hasTagCompound() && armor.getTagCompound().getBoolean("legendary")){
				// Legendary armour gives full 10 shields, like diamond.
				return ArmorMaterial.DIAMOND.getDamageReductionAmount(slot);
			}else{
				return this.getArmorMaterial().getDamageReductionAmount(slot);
			}
		}else{
			return 0;
		}
	}

	@Override
	public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot){
		if(stack.getItemDamage() < stack.getMaxDamage()){
			if(stack.getItemDamage() + damage > stack.getMaxDamage()){
				// Note for reference: this is the method to use, despite how it sounds attemptDamageItem() is called
				// by this one, not the other way round.
				stack.damageItem(stack.getMaxDamage() - stack.getItemDamage(), entity);
			}else{
				stack.damageItem(damage, entity);
			}
		}
	}
	
	@Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> subItems){
        subItems.add(new ItemStack(this, 1));
    }
	
	/** Returns the EntityEquipmentSlot for which index() returns an integer equal to the passed in slotIndex and
	 * which is an armour slot (not a hand slot). This only exists because the 1.10.2 version of Forge still uses
	 * an integer slot index in ISpecialArmor instead of an EntityEquipmentSlot. */
	// NOTE: Remove in 1.11.2
	private EntityEquipmentSlot getArmorSlotFromIndex(int slotIndex) {
		// Had to pick something to begin with.
		EntityEquipmentSlot slot = EntityEquipmentSlot.HEAD;
		
		for(EntityEquipmentSlot s : WizardryUtilities.ARMOUR_SLOTS){
			if(s.getIndex() == slotIndex){
				slot = s;
			}
		}
		return slot;
	}

}
