package com.comcast.compass;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

/**
 * Utility class to call google map static maps
 * 
 * Created by dzhou on 12/10/13.
 */

public class GoogleAPI {
	public final static int DEFAULT_SEARCH_RADIUS = 5000; // meter
    public static final int DEFAULT_RADIUS = 1000; // feet
    public static final LatLng US_CENTER = new LatLng(37.160332, -95.62501);

	public final static String PLACES_API_KEY = "AIzaSyD8mzPglNOm1G5SqR6OkCVDuym2ixK2PM4";
	public final static String STATICMAP_API_KEY = "AIzaSyCdyH1tIULffYO0ImA13OMsWqgK1uoFiRI";
	public final static String GOOGLE_API_BASE_URL = "https://maps.googleapis.com/maps/api/";

    public static LatLng getLatLngFromAddress(String addressText) {
        InputStream inputStream = null;
        String result = null;
        LatLng latLng = null;

        // format query string
        String[] queryTextArray = addressText.split(" ");
        String address = "";
        for (String s : queryTextArray) {
            if (!s.isEmpty()) {
                if (!address.isEmpty()) {
                    address += "+";
                }
                address += s;
            }
        }

        try {

            // get response from GoogleApi

            // build the URL using the latitude & longitude you want to lookup
            String url = GOOGLE_API_BASE_URL + "geocode/json?" + "address=" + address + "&sensor=false";

            inputStream = new java.net.URL(url).openStream();

            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            result = sb.toString();

            // parse json

            JSONObject json = new JSONObject(result);
            JSONArray jArray = json.getJSONArray("results");
            // get the first object
            if (jArray != null) {
                JSONObject oneObject = jArray.getJSONObject(0);
                if (oneObject != null) {
                    // pulling geometry from the array
                    JSONObject geometry = oneObject.getJSONObject("geometry");
                    if (geometry != null) {
                        JSONObject location = geometry.getJSONObject("location");
                        if (location != null) {
                            try {
                                String latStr = location.getString("lat");
                                String lngStr = location.getString("lng");
                                double lat = Double.parseDouble(latStr);
                                double lng = Double.parseDouble(lngStr);
                                latLng = new LatLng(lat, lng);
                            }
                            catch (Exception e) {
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (Exception e) {
            }
        }
        return latLng;
    }

    public static String getAddressFromLatLng(Location location) {
        return getAddressFromLatLng(location.getLatitude(), location.getLongitude(), true);
    }

    public static String getAddressFromLatLng(LatLng latLng) {
        return getAddressFromLatLng(latLng.latitude, latLng.longitude, true);
    }

    public static String getAddressFromLatLng(double lat, double lng) {
		return getAddressFromLatLng(lat, lng, true);
	}

	public static String getAddressFromLatLng(double lat, double lng, boolean includePostalcode) {
		InputStream inputStream = null;
		String result = null;
		String address = null;

		try {

			// get response from GoogleApi

			// build the URL using the latitude & longitude you want to lookup
			String url = GOOGLE_API_BASE_URL + "geocode/json?" + "latlng=" + lat + "," + lng + "&sensor=false";

			inputStream = new java.net.URL(url).openStream();

			// json is UTF-8 by default
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder sb = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
			result = sb.toString();

			// parse json

			JSONObject json = new JSONObject(result);
			JSONArray jArray = json.getJSONArray("results");
			// get the first object
			if (jArray != null) {
				JSONObject oneObject = jArray.getJSONObject(0);
				if (oneObject != null) {
					// pulling address from the array
					String formatedAddress = oneObject.getString("formatted_address");
					String[] parts = formatedAddress.split(",");
					address = "";

					if (parts.length <= 2)
						return formatedAddress;

					int end = includePostalcode ? 3 : 2;
					for (int i = 0; i < end; ++i) {
						if (address.length() != 0)
							address += ",";
						address += parts[i];
					}
				}
			}
            reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (Exception e) {
			}
		}
		return address;
	}

//    public static List<Place> searchPlaces(String queryText) {
//        return searchPlaces(queryText, null, -1, -1);
//    }
//
//	public static List<Place> searchPlaces(String queryText, LatLng center, int radius, int placeRadius) {
//		InputStream inputStream = null;
//		String result = null;
//		List<Place> searchedPlaces = new ArrayList<Place>();
//
//		if (queryText == null || queryText.isEmpty())
//			return searchedPlaces;
//
//        // format query string
//        String[] queryTextArray = queryText.split(" ");
//        String query = "";
//        for (String s : queryTextArray) {
//            if (!s.isEmpty()) {
//                if (!query.isEmpty()) {
//                    query += "+";
//                }
//                query += s;
//            }
//        }
//
//        if (placeRadius < 0)
//            placeRadius = DEFAULT_RADIUS;
//
//		try {
//			String options = "";
//			if (center == null)
//                center = US_CENTER;
//
//            if (radius <= 0)
//                radius = DEFAULT_SEARCH_RADIUS;
//            options = "&location=" + String.valueOf(center.latitude) + "," + String.valueOf(center.longitude) + "&radius=" + String.valueOf(radius);
//
//			String url = GOOGLE_API_BASE_URL + "place/textsearch/json?" + "query=" + query + options + "&sensor=false&key=" + PLACES_API_KEY;
//
//			inputStream = new java.net.URL(url).openStream();
//
//			// json is UTF-8 by default
//			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
//			StringBuilder sb = new StringBuilder();
//
//			String line = null;
//			while ((line = reader.readLine()) != null) {
//				sb.append(line + "\n");
//			}
//			result = sb.toString();
//
//			// parse json
//
//			JSONObject json = new JSONObject(result);
//			JSONArray jArray = json.getJSONArray("results");
//			if (jArray != null) {
//				int n = jArray.length();
//				for (int i = 0; i < n; ++i) {
//					JSONObject oneObject = jArray.getJSONObject(i);
//					if (oneObject != null) {
//						// pulling address from the array
//						String address = oneObject.getString("formatted_address");
//						String alias = oneObject.getString("name");
//
//						// get geometry location
//						LatLng latlng = null;
//						JSONObject geometry = oneObject.getJSONObject("geometry");
//						if (geometry != null) {
//							JSONObject location = geometry.getJSONObject("location");
//							if (location != null) {
//								try {
//									double lat = Double.parseDouble(location.getString("lat"));
//									double lng = Double.parseDouble(location.getString("lng"));
//									latlng = new LatLng(lat, lng);
//								} catch (Exception ex) {
//									ex.printStackTrace();
//								}
//							}
//						}
//
//						if (latlng!=null) {
//							JSONArray types = oneObject.getJSONArray("types");
//							String type = types.getString(0);
//							if (!type.equals("street_address")) { //type.equals("locality") || type.contains("administrative_area") || type.equals("country"))
//								address = getAddressFromLatLng(latlng.latitude, latlng.longitude, true);
//							}
//						}
//
//                        if (address != null && !address.isEmpty()) {
//                            Place place = new Place(alias, address, latlng, placeRadius);
//                            place.setAlias(alias);
//                            searchedPlaces.add(place);
//                        }
//					}
//				}
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (JSONException e) {
//			e.printStackTrace();
//		} finally {
//			try {
//				if (inputStream != null)
//					inputStream.close();
//			} catch (Exception e) {
//			}
//		}
//
//		return searchedPlaces;
//	}

    public static String getDirectionDuration(LatLng origin, LatLng destination) {
        InputStream inputStream = null;
        String result = null;
        String duration = "";

        String strOrigin = "origin="+origin.latitude+","+origin.longitude;
        String strDest = "destination="+destination.latitude+","+destination.longitude;

        String url = GOOGLE_API_BASE_URL + "directions/json"+"?"+strOrigin+"&"+strDest+"&sensor=false";

        try {
            inputStream = new java.net.URL(url).openStream();

            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            result = sb.toString();

            // parse json

            JSONObject json = new JSONObject(result);
            JSONArray jArray = json.getJSONArray("routes");
            if (jArray != null) {
                JSONObject oneObject = jArray.getJSONObject(0);
                if (oneObject != null) {
                    JSONArray legsArray = oneObject.getJSONArray("legs");
                    if (legsArray != null) {
                        JSONObject legObj = legsArray.getJSONObject(0);
                        if (legObj != null) {
                            JSONArray stepsArray = legObj.getJSONArray("steps");
                            if (stepsArray != null) {
                                JSONObject stepObj = stepsArray.getJSONObject(0);
                                if (stepObj != null) {
                                    JSONObject durationObj = stepObj.getJSONObject("duration");
                                    duration = durationObj.getString("text");
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (Exception e) {
            }
        }

        return duration;
    }

    public static String[][] getDistanceMatrix(LatLng[] origins, LatLng[] destinations) {
        if (origins == null || origins.length == 0 || destinations == null || destinations.length == 0)
            return null;

        String[][] matrix = new String[origins.length][destinations.length];

        InputStream inputStream = null;
        String result = null;
        String duration = "";

        String strOrigin = "origins=" + origins[0].latitude + "," + origins[0].longitude;
        for (int i=1; i<origins.length; ++i) {
            strOrigin += "|" + origins[i].latitude + "," + origins[i].longitude;
        }
        String strDest = "destinations="+destinations[0].latitude+","+destinations[0].longitude;
        for (int i=1; i<destinations.length; ++i) {
            strDest += "|" + destinations[i].latitude + "," + destinations[i].longitude;
        }

        String url = GOOGLE_API_BASE_URL + "distancematrix/json"+"?"+strOrigin+"&"+strDest+"&sensor=false";

        try {
            inputStream = new java.net.URL(url).openStream();

            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            result = sb.toString();

            // parse json

            JSONObject json = new JSONObject(result);
            JSONArray jArray = json.getJSONArray("rows");
            if (jArray != null) {
                for (int i=0; i< jArray.length(); ++i) {
                    JSONObject oneObject = jArray.getJSONObject(i); // one row
                    if (oneObject != null) {
                        JSONArray elementsArray = oneObject.getJSONArray("elements");
                        if (elementsArray != null) {
                            for (int j=0; j<elementsArray.length(); ++j) {
                                JSONObject elementObj = elementsArray.getJSONObject(j);
                                if (elementObj != null) {
                                    if (elementObj.has("duration")) {
                                        JSONObject durationObj = elementObj.getJSONObject("duration");
                                        duration = durationObj.getString("text");
                                        matrix[i][j] = duration;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (Exception e) {
            }
        }

        return matrix;
    }

    public static Bitmap getGoogleStaticMap(String location, int zoom, int width, int height) {
        return getGoogleStaticMap(location, zoom, width, height, null);
    }

    public static Bitmap getGoogleStaticMap(String location, int zoom, int width, int height, String marker) {
        Bitmap map = null;

        int scale = 1;
        if (width > 640 || height > 640) {
            scale = 2;
            width /= scale;
            height /= scale;
        }

        String markers = "";
        if (marker != null && !marker.isEmpty())
            markers = "&markers=" + marker + location;

        try {
            String url = GOOGLE_API_BASE_URL + "staticmap?" +
                    "center=" + location +
                    "&zoom=" + zoom +
                    "&size=" + width + "x" + height +
                    "&scale=" + scale +
                    markers +
                    "&sensor=false" + "&key=" + STATICMAP_API_KEY;
            map = BitmapFactory.decodeStream(new java.net.URL(url).openStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

}
