//passsive microwave code
var MB_TP = ee.FeatureCollection("users/mabdelka/MB_TP"),
    MB_st = ee.FeatureCollection("users/mabdelka/MB_st"),
    grid_36km = ee.FeatureCollection("users/mabdelka/grid_36km"),
    grid_3km = ee.FeatureCollection("users/mabdelka/grid_3km"),
    grid_9km = ee.FeatureCollection("users/mabdelka/grid_9km"),
    geometry = /* color: #dddddd */ee.Geometry.MultiPoint();
	
                      // A simple tool for charting SMAP surface soil moisture.

/*
 * Map layer configuration
 */
 
Map.setOptions('HYBRID');
////////////////////////////////////////////////////////////////////////////////////////////////////
// Create the main map and set the ssm layer///////////////////////////////////////////////////////

var Start_period = ee.Date('2015-04-02')
var End_period = ee.Date(new Date().getTime())

var geometry = ee.Geometry.Point([-73.579361, 41.923697]);
var dataset = ee.ImageCollection('NASA_USDA/HSL/SMAP10KM_soil_moisture')
                  .filter(ee.Filter.date(Start_period, End_period));
var soilMoisture = dataset.select('ssm');
var soilMoistureVis = {
  min: 0.0,
  max: 28.0,
  palette: ['ff0303', 'efff07', 'efff07', '418504', '0300ff'],    
};
Map.addLayer(soilMoisture, soilMoistureVis, 'Soil Moisture');
Map.setCenter(-73.579361, 41.923697, 10) //23 Charlie Hill Rd., Millerton ---- Change zoom if needed min:0 max:24

// The ROI and SMAP footprint (3-9-36 km grid) are initially unchecked

var Grid_3km=Map.addLayer(grid_3km, {'color': 'blue'},'SMAP_Grid:[3x3-KM]');
Grid_3km.setShown(0);

var Grid_9km = Map.addLayer(grid_9km, {'color': 'red'},'SMAP_Grid:[9x9-KM]');
Grid_9km.setShown(0);

var Grid_36km = Map.addLayer(grid_36km, {'color': 'yellow'},'SMAP_Grid:[36x36-KM]');
Grid_36km.setShown(0);


// Display Tiessen Polygon at the Millbrook site (unchecked)
var T_poly=Map.addLayer(MB_TP, {},'MB_Thiessen_Poly');

T_poly.setShown(0);

// Display stations at the Millbrook site
var text = require ('users/gena/packages:text');
var shp = ee.FeatureCollection(MB_st);

Map.addLayer(shp, {},'MB_Stations');
Map.centerObject(shp,10); ////// Change zoom if needed min:0 max:24

// Station ID

var scale = Map.getScale() / 10;
var labels = shp.map(function(feat) {
  feat = ee.Feature(feat);
  var name = ee.String(feat.get("Station_ID"));
  var centroid = feat.geometry().centroid();
  var t = text.draw (name, centroid, scale, {
    fontSize: 10,
    textColor: 'red',
    outlineWidth: 0.4,
    outlineColor: 'red'
  });
  return t;
});
var labels_final = ee.ImageCollection (labels);
Map.addLayer(labels_final, {}, "Station_ID");

//Set the drawing Area panel

var drawingTools = Map.drawingTools();
drawingTools.setShown(false);
while (drawingTools.layers().length() > 0) {
  var layer = drawingTools.layers().get(0);
  drawingTools.layers().remove(layer);
}
var dummyGeometry =
    ui.Map.GeometryLayer({geometries: null, name: 'geometry', color: 'dddddd'});

drawingTools.layers().add(dummyGeometry);
function clearGeometry() {
  var layers = drawingTools.layers();
  layers.get(0).geometries().remove(layers.get(0).geometries().get(0));
}
function drawRectangle() {
  clearGeometry();
  drawingTools.setShape('rectangle');
  drawingTools.draw();
}

function drawPolygon() {
  clearGeometry();
  drawingTools.setShape('polygon');
  drawingTools.draw();
}

function drawPoint() {
  clearGeometry();
  drawingTools.setShape('point');
  drawingTools.draw();
}
var chartPanel = ui.Panel({
  style:
      {height: '235px', width: '600px', position: 'bottom-right', shown: false}
});
Map.add(chartPanel);

function chartTimeSeries() {
  // Make the chart panel visible the first time a geometry is drawn.
  if (!chartPanel.style().get('shown')) {
    chartPanel.style().set('shown', true);
  }

  // Get the drawn geometry; it will define the reduction region.
  var aoi = drawingTools.layers().get(0).getEeObject();

  // Set the drawing mode back to null; turns drawing off.
  drawingTools.setShape(null);

  // Reduction scale is based on map scale to avoid memory/timeout errors.
  var mapScale = Map.getScale();
  var scale = mapScale > 5000 ? mapScale * 2 : 5000;

  // Chart time series for the selected area of interest.
  var chart = ui.Chart.image
                  .seriesByRegion({
                    imageCollection: ee.ImageCollection('NASA_USDA/HSL/SMAP10KM_soil_moisture'),
                    regions: aoi,
                    reducer: ee.Reducer.mean(),
                    band: 'ssm',
                    scale: scale,
                    xProperty: 'system:time_start'
                  })
                  .setOptions({
                     title: 'SMAP Surface Soil Moisture Time Series',
                     vAxis: {title: 'ssm (mm)'},
                     hAxis: {title: 'Date', format: 'MM-yy', gridlines: {count: 7}},
                  
                   series: {
                  0: {
                  color: 'blue',
                  lineWidth: 0,
                  pointsVisible: true,
                  pointSize: 2,
                   },
                 },
    trendlines: {0: {
        color: 'CC0000'
      }},
      lineWidth: 0.2,
      pointSize: 0.2,
   
    legend: {position: 'right'},
                  });

  // Replace the existing chart in the chart panel with the new chart.
  chartPanel.widgets().reset([chart]);
}
drawingTools.onDraw(ui.util.debounce(chartTimeSeries, 500));
drawingTools.onEdit(ui.util.debounce(chartTimeSeries, 500));

var symbol = {
  rectangle: '🟥',
  polygon: '🛑',
  point: '📍',
};
var controlPanel = ui.Panel({
  widgets: [
    ui.Label('1. Select a drawing mode.'),
    ui.Button({
      label: symbol.rectangle + ' Rectangle',
      onClick: drawRectangle,
      style: {stretch: 'horizontal'}
    }),
    ui.Button({
      label: symbol.polygon + ' Polygon',
      onClick: drawPolygon,
      style: {stretch: 'horizontal'}
    }),
    ui.Button({
      label: symbol.point + ' Point',
      onClick: drawPoint,
      style: {stretch: 'horizontal'}
    }),
    ui.Label('2. Draw a geometry.'),
    ui.Label('3. Wait for chart to render.'),
    ui.Label(
        '4. Repeat 1-3 or edit/move\ngeometry for a new chart.',
        {whiteSpace: 'pre'})
  ],
  style: {position: 'bottom-left'},
  layout: null,
});

Map.add(controlPanel);


//Gradient Legend
// set position of panel
var legend = ui.Panel({
style: {
position: 'bottom-right',
padding: '8px 10px'
}
});
// Create legend title
var legendTitle = ui.Label({
value: 'Soil Moisture (mm)',
style: {
fontWeight: 'bold',
fontSize: '18px',
margin: '0 0 4px 0',
padding: '0'
}
});
legend.add(legendTitle);
// create the legend image
var lon = ee.Image.pixelLonLat().select('latitude');
var gradient = lon.multiply((soilMoistureVis.max-soilMoistureVis.min)/100.0).add(soilMoistureVis.min);
var legendImage = gradient.visualize(soilMoistureVis);
// create text on top of legend
var panel = ui.Panel({
widgets: [
ui.Label(soilMoistureVis['max'])
],
});
legend.add(panel);
// create thumbnail from the image
var thumbnail = ui.Thumbnail({
image: legendImage,
params: {bbox:'0,0,10,100', dimensions:'10x150'},
style: {padding: '1px', position: 'bottom-center'}
});
// add the thumbnail to the legend
legend.add(thumbnail);
// create text on top of legend
var panel = ui.Panel({
widgets: [
ui.Label(soilMoistureVis['min'])
],
});
legend.add(panel);
Map.add(legend);


//Date Slider
ee.Dictionary({start: Start_period, end: End_period})
  .evaluate(renderSlider) 

function renderSlider(dates) {
  var slider = ui.DateSlider({
    start: dates.start.value, 
    end: dates.end.value, 
    period:3 , // Every 3 days
    onChange: renderDateRange
  })
  Map.add(slider)
}

function renderDateRange(dateRange) {
 var image = soilMoisture
  .filterDate(dateRange.start(), dateRange.end())
    .mean()
 
 Map.addLayer(image, soilMoistureVis, '3-days averaged ssm');
  var layer = ui.Map.Layer(image, soilMoistureVis, '3-days averaged ssm')
 // Map.layers().reset([layer])
}


//LOGOS & TEXT
var table = ui.Chart(
[
  ['<h5><b>NASA-USDA Enhanced SMAP Global Soil Moisture Data</h5><b>'],
  ['<img src=http://web.stevens.edu/news/newspoints/brand-logos/2020/Stacked/Stevens-Stacked-Logo-2020_4C.svg width=150px>'],
  ['<img src=https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/NASA_logo.svg/300px-NASA_logo.svg.png width=150px>'],  
  ['<h10>App Created By: Peter Shikhman & David Valle-Montesdeoca</h10>'],
  ['<h10>This app allows the user to visualize soil moisture data from the NASA SMAP satellite. Graph displays soil moisture from 4/2/15 to the latest data available.</h10>'],
  ['<h10>Making predictions about the weather and the atmosphere on Earth has always been a challenge. In an attempt to have better predictions, NASA has set up a satellite mission (SMAP - Soil Moisture Active and Passive) in order to collect necessary data through active and passive sensors on Earth’s soil. Through the NASA SMAP Mission, it is possible to collect data on a large scale multiple times a day for analysis on more accurate predictions on weather, drought, floods, crop productivity, and water and carbon cycles on Earth.</h10>'],
  ['<h10>It would be too costly and nearly impossible to set up sensors around the world to collect the same data SMAP is capturing. A satellite in space also does not require maintenance as ground sensors do. While SMAP is the best method to collect large-scale soil moisture data, it is necessary to ensure that data collected worldwide have similar/identical calibration methods for accurate results. Through a local test site in Millbrook, New York, we are able to create a bare soil sensing environment and test the soil simultaneously as the satellite passes over the plot of land. The soil is tested three times a day, from early morning to afternoon and early evening. These three testing times allow for a full scope of the possible soil moisture as the water content changes throughout the day depending on the ambient temperature. Knowing this information we can apply mathematical models to other regions of the same terrain for the most accurate satellite data possible with current technology.</h10>'],
  ['<h10>https://tinyurl.com/SMAPDataCatalog</h10>'],
],
'Table', {allowHtml: true});

var titlePanel = ui.Panel([table], 'flow', {width: '300px', padding: '8px'});
ui.root.insert(0, titlePanel);














