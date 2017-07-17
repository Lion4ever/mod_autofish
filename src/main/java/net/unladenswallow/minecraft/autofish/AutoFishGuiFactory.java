package net.unladenswallow.minecraft.autofish;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

public class AutoFishGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public boolean hasConfigGui() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public GuiScreen createConfigGui(GuiScreen parentScreen) {
		// TODO Auto-generated method stub
		return null;
	}

}
