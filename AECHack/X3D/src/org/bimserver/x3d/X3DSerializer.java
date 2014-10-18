package org.bimserver.x3d;

/******************************************************************************
 * Copyright (C) 2009-2012  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Modified from the Collada serializer March 15 2014 for AEC Hackathon
 *****************************************************************************/

import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bimserver.emf.IdEObject;
import org.bimserver.models.ifc2x3tc1.IfcBuildingElementProxy;
import org.bimserver.models.ifc2x3tc1.IfcColumn;
import org.bimserver.models.ifc2x3tc1.IfcCurtainWall;
import org.bimserver.models.ifc2x3tc1.IfcDoor;
import org.bimserver.models.ifc2x3tc1.IfcFeatureElementSubtraction;
import org.bimserver.models.ifc2x3tc1.IfcFlowSegment;
import org.bimserver.models.ifc2x3tc1.IfcFurnishingElement;
import org.bimserver.models.ifc2x3tc1.IfcMember;
import org.bimserver.models.ifc2x3tc1.IfcPlate;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcProject;
import org.bimserver.models.ifc2x3tc1.IfcRailing;
import org.bimserver.models.ifc2x3tc1.IfcRoof;
import org.bimserver.models.ifc2x3tc1.IfcSIUnit;
import org.bimserver.models.ifc2x3tc1.IfcSlab;
import org.bimserver.models.ifc2x3tc1.IfcSlabTypeEnum;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.models.ifc2x3tc1.IfcStair;
import org.bimserver.models.ifc2x3tc1.IfcStairFlight;
import org.bimserver.models.ifc2x3tc1.IfcUnit;
import org.bimserver.models.ifc2x3tc1.IfcUnitAssignment;
import org.bimserver.models.ifc2x3tc1.IfcUnitEnum;
import org.bimserver.models.ifc2x3tc1.IfcWall;
import org.bimserver.models.ifc2x3tc1.IfcWallStandardCase;
import org.bimserver.models.ifc2x3tc1.IfcWindow;
import org.bimserver.models.store.SIPrefix;
import org.bimserver.plugins.PluginManager;
import org.bimserver.plugins.ifcengine.IfcEngine;
import org.bimserver.plugins.ifcengine.IfcEngineException;
import org.bimserver.plugins.ifcengine.IfcEngineGeometry;
import org.bimserver.plugins.ifcengine.IfcEngineInstance;
import org.bimserver.plugins.ifcengine.IfcEngineInstanceVisualisationProperties;
import org.bimserver.plugins.ifcengine.IfcEngineModel;
import org.bimserver.plugins.serializers.EmfSerializer;
import org.bimserver.plugins.serializers.IfcModelInterface;
import org.bimserver.plugins.serializers.ProjectInfo;
import org.bimserver.plugins.serializers.SerializerException;
import org.bimserver.utils.UTF8PrintWriter;
import org.eclipse.emf.common.util.EList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class X3DSerializer extends EmfSerializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(X3DSerializer.class);
	private static final Map<Class<? extends IfcProduct>, Convertor<? extends IfcProduct>> convertors = new LinkedHashMap<Class<? extends IfcProduct>, Convertor<? extends IfcProduct>>();
	private final Map<String, Set<String>> converted = new HashMap<String, Set<String>>();
	private SIPrefix lengthUnitPrefix;
	private IfcEngineModel ifcEngineModel;
	private IfcEngineGeometry geometry;
	private int idCounter;

	private static <T extends IfcProduct> void addConvertor(Convertor<T> convertor) {
		convertors.put(convertor.getCl(), convertor);
	}

	static {
		addConvertor(new Convertor<IfcRoof>(IfcRoof.class, new double[] { 0.837255f, 0.203922f, 0.270588f }, 1.0f));
		addConvertor(new Convertor<IfcSlab>(IfcSlab.class, new double[] { 0.637255f, 0.603922f, 0.670588f }, 1.0f) {
			@Override
			public String getMaterialName(Object ifcSlab) {
				if (ifcSlab == null || !(ifcSlab instanceof IfcSlab) || ((IfcSlab) ifcSlab).getPredefinedType() != IfcSlabTypeEnum.ROOF) {
					return "IfcSlab";
				} else {
					return "IfcRoof";
				}
			}
		});
		addConvertor(new Convertor<IfcWindow>(IfcWindow.class, new double[] { 0.2f, 0.2f, 0.8f }, 0.2f));
		addConvertor(new Convertor<IfcSpace>(IfcSpace.class, new double[] { 0.5f, 0.4f, 0.1f }, 0.2f));
		addConvertor(new Convertor<IfcDoor>(IfcDoor.class, new double[] { 0.637255f, 0.603922f, 0.670588f }, 1.0f));
		addConvertor(new Convertor<IfcStair>(IfcStair.class, new double[] { 0.637255f, 0.603922f, 0.670588f }, 1.0f));
		addConvertor(new Convertor<IfcStairFlight>(IfcStairFlight.class, new double[] { 0.637255f, 0.603922f, 0.670588f }, 1.0f));
		addConvertor(new Convertor<IfcFlowSegment>(IfcFlowSegment.class, new double[] { 0.6f, 0.4f, 0.5f }, 1.0f));
		addConvertor(new Convertor<IfcFurnishingElement>(IfcFurnishingElement.class, new double[] { 0.437255f, 0.603922f, 0.370588f }, 1.0f));
		addConvertor(new Convertor<IfcPlate>(IfcPlate.class, new double[] { 0.437255f, 0.603922f, 0.370588f }, 1.0f));
		addConvertor(new Convertor<IfcMember>(IfcMember.class, new double[] { 0.437255f, 0.603922f, 0.370588f }, 1.0f));
		addConvertor(new Convertor<IfcWallStandardCase>(IfcWallStandardCase.class, new double[] { 0.537255f, 0.337255f, 0.237255f }, 1.0f));
		addConvertor(new Convertor<IfcWall>(IfcWall.class, new double[] { 0.537255f, 0.337255f, 0.237255f }, 1.0f));
		addConvertor(new Convertor<IfcCurtainWall>(IfcCurtainWall.class, new double[] { 0.5f, 0.5f, 0.5f }, 0.5f));
		addConvertor(new Convertor<IfcRailing>(IfcRailing.class, new double[] { 0.137255f, 0.203922f, 0.270588f }, 1.0f));
		addConvertor(new Convertor<IfcColumn>(IfcColumn.class, new double[] { 0.437255f, 0.603922f, 0.370588f, }, 1.0f));
		addConvertor(new Convertor<IfcBuildingElementProxy>(IfcBuildingElementProxy.class, new double[] { 0.5f, 0.5f, 0.5f }, 0.8f));
		addConvertor(new Convertor<IfcProduct>(IfcProduct.class, new double[] { 0.5f, 0.5f, 0.5f }, 1.0f));
	}

	@Override
	public void init(IfcModelInterface model, ProjectInfo projectInfo, PluginManager pluginManager, IfcEngine ifcEngine) throws SerializerException {
		super.init(model, projectInfo, pluginManager, ifcEngine);
		try {
			EmfSerializer serializer = getPluginManager().requireIfcStepSerializer();
			serializer.init(model, getProjectInfo(), getPluginManager(), ifcEngine);
			ifcEngine.init();
			ifcEngineModel = ifcEngine.openModel(serializer.getBytes());
			ifcEngineModel.setPostProcessing(true);
			geometry = ifcEngineModel.finalizeModelling(ifcEngineModel.initializeModelling());
		} catch (Exception e) {
			throw new SerializerException(e);
		}
	}

	@Override
	protected void reset() {
		setMode(Mode.BODY);
	}

	@Override
	public boolean write(OutputStream out) throws SerializerException {
		if (getMode() == Mode.BODY) {
			PrintWriter writer = new UTF8PrintWriter(out);
			try {
				writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
				writer.println("<X3D profile='Immersive'>");

				writeAssets(writer); // WorldInfo and metadata
				writer.println(" <Scene>");
//				writeCameras(writer);
//				writeLights(writer);
//				writeMaterials(writer);
				writeAppearances(writer);
				writeGeometries(writer);
				writeVisualScenes(writer);
//				writeScene(writer);

				writer.println(" </Scene>");
				writer.print("</X3D>");
				writer.flush();
			} catch (Exception e) {
				LOGGER.error("", e);
			}
			writer.flush();
			setMode(Mode.FINISHED);
			getIfcEngine().close();
			return true;
		}
		else if (getMode() == Mode.FINISHED)
		{
			return false;
		}
		return false;
	}

	private void writeAssets(PrintWriter out) {
		out.println("    <head>");
		out.println("        <meta name='author' content='"+ (getProjectInfo() == null ? "" : getProjectInfo().getAuthorName()) + "'/>");
		out.println("        <meta name='authoring_tool' content='BIMserver'/>");
		out.println("        <meta name='comments' content='" + (getProjectInfo() == null ? "" : getProjectInfo().getDescription()) + "'/>");
		out.println("        <meta name='copyright' content='Copyright'/>");
		out.println("        <meta name='' content='Copyright'/>");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
		String date = dateFormat.format(new Date());
		out.println("        <meta name='created' content='" + date + "'/>");
		out.println("        <meta name='modified' content='" + date + "'/>");
		out.println("    </head>");
	}

	private void writeGeometries(PrintWriter out) throws IfcEngineException, SerializerException {
		out.println("  <Group DEF='LibOfMeshes'>");
		out.println("   <Switch whichChoice='-1'>");

		Set<IfcProduct> convertedObjects = new HashSet<IfcProduct>();

		for (Class<? extends IfcProduct> cl : convertors.keySet()) {
			Convertor<? extends IfcProduct> convertor = convertors.get(cl);
			for (IfcProduct object : model.getAllWithSubTypes(cl)) {
				if (!convertedObjects.contains(object)) {
					convertedObjects.add(object);
					setGeometry(out, object, convertor.getMaterialName(object));
				}
			}
		}
		out.println("   </Switch>");
		out.println("  </Group>");
	}

	private String generateId(IfcProduct ifcProductObject) {
		if (ifcProductObject.getGlobalId() != null && ifcProductObject.getGlobalId().getWrappedValue() != null) {
			return ifcProductObject.getGlobalId().getWrappedValue();
		//	ifcProductObject.getName()
		}
		return "" + (idCounter++);
	}

	private void setGeometry(PrintWriter out, IfcProduct ifcProductObject, String material) throws IfcEngineException, SerializerException {
		// boolean materialFound = false;
		// boolean added = false;
		// if (ifcRootObject instanceof IfcProduct) {
		// IfcProduct ifcProduct = (IfcProduct) ifcRootObject;
		//
		// EList<IfcRelDecomposes> isDecomposedBy =
		// ifcProduct.getIsDecomposedBy();
		// for (IfcRelDecomposes dcmp : isDecomposedBy) {
		// EList<IfcObjectDefinition> relatedObjects = dcmp.getRelatedObjects();
		// for (IfcObjectDefinition relatedObject : relatedObjects) {
		// setGeometry(out, relatedObject, material);
		// }
		// }
		// if (isDecomposedBy != null && isDecomposedBy.size() > 0) {
		// return;
		// }
		//
		// Iterator<IfcRelAssociatesMaterial> ramIter =
		// model.getAll(IfcRelAssociatesMaterial.class).iterator();
		// boolean found = false;
		// IfcMaterialSelect relatingMaterial = null;
		// while (!found && ramIter.hasNext()) {
		// IfcRelAssociatesMaterial ram = ramIter.next();
		// if (ram.getRelatedObjects().contains(ifcProduct)) {
		// found = true;
		// relatingMaterial = ram.getRelatingMaterial();
		// }
		// }
		// if (found && relatingMaterial instanceof IfcMaterialLayerSetUsage) {
		// IfcMaterialLayerSetUsage mlsu = (IfcMaterialLayerSetUsage)
		// relatingMaterial;
		// IfcMaterialLayerSet forLayerSet = mlsu.getForLayerSet();
		// if (forLayerSet != null) {
		// EList<IfcMaterialLayer> materialLayers =
		// forLayerSet.getMaterialLayers();
		// for (IfcMaterialLayer ml : materialLayers) {
		// IfcMaterial ifcMaterial = ml.getMaterial();
		// if (ifcMaterial != null) {
		// String name = ifcMaterial.getName();
		// String filterSpaces = fitNameForQualifiedName(name);
		// materialFound = converted.containsKey(filterSpaces);
		// if (materialFound) {
		// material = filterSpaces;
		// }
		// }
		// }
		// }
		// } else if (found && relatingMaterial instanceof IfcMaterial) {
		// IfcMaterial ifcMaterial = (IfcMaterial) relatingMaterial;
		// String name = ifcMaterial.getName();
		// String filterSpaces = fitNameForQualifiedName(name);
		// materialFound = converted.containsKey(filterSpaces);
		// if (materialFound) {
		// material = filterSpaces;
		// }
		// }
		//
		// if (!materialFound) {
		// IfcProductRepresentation representation =
		// ifcProduct.getRepresentation();
		// if (representation instanceof IfcProductDefinitionShape) {
		// IfcProductDefinitionShape pds = (IfcProductDefinitionShape)
		// representation;
		// EList<IfcRepresentation> representations = pds.getRepresentations();
		// for (IfcRepresentation rep : representations) {
		// if (rep instanceof IfcShapeRepresentation) {
		// IfcShapeRepresentation sRep = (IfcShapeRepresentation) rep;
		// EList<IfcRepresentationItem> items = sRep.getItems();
		// for (IfcRepresentationItem item : items) {
		// EList<IfcStyledItem> styledByItem = item.getStyledByItem();
		// for (IfcStyledItem sItem : styledByItem) {
		// EList<IfcPresentationStyleAssignment> styles = sItem.getStyles();
		// for (IfcPresentationStyleAssignment sa : styles) {
		// EList<IfcPresentationStyleSelect> styles2 = sa.getStyles();
		// for (IfcPresentationStyleSelect pss : styles2) {
		// if (pss instanceof IfcSurfaceStyle) {
		// IfcSurfaceStyle ss = (IfcSurfaceStyle) pss;
		// String name = ss.getName();
		// String filterSpaces = fitNameForQualifiedName(name);
		// added = true;
		// if (!converted.containsKey(filterSpaces)) {
		// converted.put(filterSpaces, new HashSet<String>());
		// }
		// converted.get(filterSpaces).add(id);
		// }
		// }
		// }
		// }
		// }
		// }
		// }
		// }
		// }
		// }
		//
		// if (!added) {
		// }

		if (ifcProductObject instanceof IfcFeatureElementSubtraction) {
			// Mostly just skips IfcOpeningElements which one would probably not
			// want to end up in the Collada file.
			return;
		}

		IfcEngineInstance instance = ifcEngineModel.getInstanceFromExpressId((int) ifcProductObject.getOid());
		IfcEngineInstanceVisualisationProperties visualisationProperties = instance.getVisualisationProperties();
		if (visualisationProperties.getPrimitiveCount() > 0) {
			String id = generateId(ifcProductObject);
			if (!converted.containsKey(material)) {
				converted.put(material, new HashSet<String>());
			}
			converted.get(material).add(id);

			String name = "Unknown";
			if (ifcProductObject.getGlobalId() != null && ifcProductObject.getGlobalId().getWrappedValue() != null) {
				name = ifcProductObject.getGlobalId().getWrappedValue();
			}

			out.println("     <Shape>");
			out.println("      <IndexedTriangleSet DEF='geom-" + id + "' containerField='geometry' index='");
			int count = visualisationProperties.getPrimitiveCount() * 3;
			for (int i = 0; i < count; i += 3) {
				out.print(i + " ");
				out.print((i + 2)  + " ");
				out.print(i + 1);
				if (i + 3 != count) {
					out.print(" ");
				}
			}
			out.println("'>");
			out.println("       <Coordinate point='");

			count = visualisationProperties.getPrimitiveCount() * 3 + visualisationProperties.getStartIndex();
			for (int i = visualisationProperties.getStartIndex(); i < count; i++) {
				int index = geometry.getIndex(i) * 3;
				out.print(geometry.getVertex(index + 0) + " ");
				out.print(geometry.getVertex(index + 1) + " ");
				out.print(geometry.getVertex(index + 2));
				if (i != count - 1) {
					out.print(" ");
				}
			}
			out.println("'/>");
			out.println("      </IndexedTriangleSet>");
			out.println("     </Shape>");
		}
	}
	
	private void writeVisualScenes(PrintWriter out) {
		for (String material : converted.keySet()) {
			Set<String> ids = converted.get(material);
			for (String id : ids) {
				out.println("            <Shape DEF='shape-" + id + "'>");
				out.println("                <Appearance USE='" + material + "-app'/>");
				out.println("                <IndexedTriangleSet USE='geom-" + id + "'/>");
				out.println("            </Shape>");
			}
		}
	}

	
/*
	private void writeMaterials(PrintWriter out) {
		out.println("      <library_effects>");
		for (Convertor<? extends IfcProduct> convertor : convertors.values()) {
			writeMaterial(out, convertor.getMaterialName(null), convertor.getColors(), convertor.getOpacity());
		}
		// List<IfcSurfaceStyle> listSurfaceStyles =
		// model.getAll(IfcSurfaceStyle.class);
		// for (IfcSurfaceStyle ss : listSurfaceStyles) {
		// EList<IfcSurfaceStyleElementSelect> styles = ss.getStyles();
		// for (IfcSurfaceStyleElementSelect style : styles) {
		// if (style instanceof IfcSurfaceStyleRendering) {
		// IfcSurfaceStyleRendering ssr = (IfcSurfaceStyleRendering) style;
		// IfcColourRgb colour = null;
		// IfcColourOrFactor surfaceColour = ssr.getSurfaceColour();
		// if (surfaceColour instanceof IfcColourRgb) {
		// colour = (IfcColourRgb) surfaceColour;
		// }
		// String name = fitNameForQualifiedName(ss.getName());
		// writeMaterial(out, name, new double[] { colour.getRed(),
		// colour.getGreen(), colour.getBlue() }, (ssr.isSetTransparency() ?
		// (ssr.getTransparency()) : 1.0f));
		// break;
		// }
		// }
		// }
		out.println("    </library_effects>");
	}
*/
	// private String fitNameForQualifiedName(String name) {
	// if (name == null) {
	// return "";
	// }
	// StringBuilder builder = new StringBuilder(name);
	// int indexOfSpace = builder.indexOf(" ");
	// while (indexOfSpace >= 0) {
	// builder.deleteCharAt(indexOfSpace);
	// indexOfSpace = builder.indexOf(" ");
	// }
	// indexOfSpace = builder.indexOf(",");
	// while (indexOfSpace >= 0) {
	// builder.setCharAt(indexOfSpace, '_');
	// indexOfSpace = builder.indexOf(",");
	// }
	// indexOfSpace = builder.indexOf("/");
	// while (indexOfSpace >= 0) {
	// builder.setCharAt(indexOfSpace, '_');
	// indexOfSpace = builder.indexOf("/");
	// }
	// indexOfSpace = builder.indexOf("*");
	// while (indexOfSpace >= 0) {
	// builder.setCharAt(indexOfSpace, '_');
	// indexOfSpace = builder.indexOf("/");
	// }
	// return builder.toString();
	// }

	private void writeMaterial(PrintWriter out, String name, double[] colors, double transparency) {
		out.println("        <Material DEF='" + name + "-mat'");
		out.println("         containerField='material'");
		out.println("         ambientIntensity='.2'");
		out.println("         shininess='.2'");
		out.println("         diffuseColor='" + colors[0] + " " + colors[1] + " " + colors[2] + "'");
		out.println("         emissiveColor='0 0 0'");
		out.println("         transparency='" + (1-transparency) + "'");
		out.println("        />");
	}

	private void writeLights(PrintWriter out) {
		out.println("    <library_lights>");
		out.println("        <light id=\"light-lib\" name=\"light\">");
		out.println("            <technique_common>");
		out.println("                <point>");
		out.println("                    <color>1 1 1</color>");
		out.println("                    <constant_attenuation>1</constant_attenuation>");
		out.println("                    <linear_attenuation>0</linear_attenuation>");
		out.println("                    <quadratic_attenuation>0</quadratic_attenuation>");
		out.println("                </point>");
		out.println("            </technique_common>");
		out.println("            <technique profile=\"MAX3D\">");
		out.println("                <intensity>1.000000</intensity>");
		out.println("            </technique>");
		out.println("        </light>");
		out.println("        <light id=\"pointLightShape1-lib\" name=\"pointLightShape1\">");
		out.println("            <technique_common>");
		out.println("                <point>");
		out.println("                    <color>1 1 1</color>");
		out.println("                    <constant_attenuation>1</constant_attenuation>");
		out.println("                    <linear_attenuation>0</linear_attenuation>");
		out.println("                    <quadratic_attenuation>0</quadratic_attenuation>");
		out.println("                </point>");
		out.println("            </technique_common>");
		out.println("        </light>");
		out.println("    </library_lights>");
	}

	private void writeCameras(PrintWriter out) {
		out.println("    <library_cameras>");
		out.println("        <camera id=\"PerspCamera\" name=\"PerspCamera\">");
		out.println("            <optics>");
		out.println("                <technique_common>");
		out.println("                    <perspective>");
		out.println("                        <yfov>37.8493</yfov>");
		out.println("                        <aspect_ratio>1</aspect_ratio>");
		out.println("                        <znear>10</znear>");
		out.println("                        <zfar>1000</zfar>");
		out.println("                    </perspective>");
		out.println("                </technique_common>");
		out.println("            </optics>");
		out.println("        </camera>");
		out.println("        <camera id=\"testCameraShape\" name=\"testCameraShape\">");
		out.println("            <optics>");
		out.println("                <technique_common>");
		out.println("                    <perspective>");
		out.println("                        <yfov>37.8501</yfov>");
		out.println("                        <aspect_ratio>1</aspect_ratio>");
		out.println("                        <znear>0.01</znear>");
		out.println("                        <zfar>1000</zfar>");
		out.println("                    </perspective>");
		out.println("                </technique_common>");
		out.println("            </optics>");
		out.println("        </camera>");
		out.println("    </library_cameras>");
	}

	private void writeAppearances(PrintWriter out) {
		out.println("      <Group DEF='AppearanceLibShape'>");
		out.println("       <Switch whichChoice='-1'>");
		for (Convertor<? extends IfcProduct> convertor : convertors.values()) {
			writeAppearance(out, convertor.getMaterialName(null), convertor.getColors(), convertor.getOpacity());
		}
		out.println("       </Switch>");
		out.println("      </Group>");
	}

	private void writeAppearance(PrintWriter out, String name, double[] colors, double transparency) {
		out.println("       <Shape>");
		out.println("        <Appearance DEF='" + name + "-app' containerField='appearance'>");
		writeMaterial (out, name, colors, transparency);
		out.println("        </Appearance>");
		out.println("       </Shape>");
	}

}

