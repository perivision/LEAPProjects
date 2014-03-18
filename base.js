  $(document).ready(function() {
    var ctl = new Leap.Controller({enableGestures: true});

    var swiper = ctl.gesture('swipe');

    var tolerance = 50;
    var cooloff = 300;


    var slider = _.debounce(function(xDir, yDir) {
      /*
      x += xDir;
      x = (x + 5) % 5;
      y += yDir;
      y = (y + 5) % 5;
      console.log("x:"+x);
      console.log("y:"+y);
      */
      //console.log('.grid #d'+x+"_"+y);
      toggleRendering(xDir, yDir);
      //updateHighlight();
    }, cooloff);

    swiper.update(function(g) {
      if (Math.abs(g.translation()[0]) > tolerance || Math.abs(g.translation()[1]) > tolerance) {
        var xDir = Math.abs(g.translation()[0]) > tolerance ? (g.translation()[0] > 0 ? -1 : 1) : 0;
        var yDir = Math.abs(g.translation()[1]) > tolerance ? (g.translation()[1] < 0 ? -1 : 1) : 0;
        slider(xDir, yDir);
        
      }
    });

    ctl.connect();
   //updateHighlight();


  })


  function toggleRendering(xDir, yDir)
    {
   
     


      var textObject = document.getElementsByTagName("Text");
      var text = textObject[0];
      var textVal = '';

      //console.log(xDir+4);
      //console.log(yDir);

      if(xDir>0) { textVal='Right'; }
      if(xDir<0) { textVal='Left'; }
      if(yDir>0) { textVal='Close'; }
      if(yDir<0) { textVal='Open';  }
  
      text.setAttribute('string', textVal);

      //var shadestate = get_url_params('http://smarterwindow.lbl.gov/sensors/all');
      //alert(get_url_params('http://smarterwindow.lbl.gov/sensors/all'));

      loadXMLDoc ('http://smarterwindow.lbl.gov/actions/override?timeout_seconds=1200&api_key=AEChackathon2');

      //if(!shadestate) {alert('fail');}
      var shadeClosed = true;
      var results = [];
      var searchField = "currentShadeState";
      
      for (var i=0 ; i < shadestate.list.length ; i++)
      {
          if (shadestate.list[i][searchField] == 'Closed') {
              shadeClosed = true; alert ('opening');
          } 
          if (shadestate.list[i][searchField] == 'Opened') {
              shadeClosed = false; alert ('closing');
          } 
      }

      if(textVal=='Down' && shadeClosed==false) {
        loadXMLDoc ('http://smarterwindow.lbl.gov/actions/override?timeout_seconds=120&api_key=AEChackathon');
      }
      if(textVal=='Up' && shadeClosed==true) {
        loadXMLDoc ('http://smarterwindow.lbl.gov/actions/override?timeout_seconds=120&api_key=AEChackathon');
      }
      
      
      return false;
    }

function get_url_params(u){
    
          var theURL = u;
          var JS_GET = new Object();
          var splitURL = theURL.split('?');
         
          if(splitURL.length>1){
               var splitVars = splitURL[1].split('&');
               for(i=0; i< splitVars.length; i++){
                    splitPair = splitVars[i].split('=');
                    JS_GET[splitPair[0]] = splitPair[1];
               }//end for
         
               return JS_GET;
         
          }//end if
          else{
              
               return false;
              
          }
    
     }//end get_url_params 


var xmlhttp;

function loadXMLDoc(url)
{
    xmlhttp=null;
if (window.XMLHttpRequest)
  {// code for all new browsers
      xmlhttp=new XMLHttpRequest();
  }
else if (window.ActiveXObject)
  {// code for IE5 and IE6
      xmlhttp=new ActiveXObject("Microsoft.XMLHTTP");
  }
if (xmlhttp!=null)
  {
      xmlhttp.onreadystatechange=state_Change;
      xmlhttp.open("GET",url,true);
      xmlhttp.send(null);
  }
else
  {
      alert("Your browser does not support XMLHTTP.");
  }
}

function state_Change()
{
    if (xmlhttp.readyState==4)
      {// 4 = "loaded"
          if (xmlhttp.status==200)
            {// 200 = OK
             console.log(xmlhttp);
            // ...our code here...
        }
  else
        {
            
        }
  }
}