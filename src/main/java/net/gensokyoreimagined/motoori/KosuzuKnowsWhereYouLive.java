// Kosuzu Copyright (C) 2024 Gensokyo Reimagined
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package net.gensokyoreimagined.motoori;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.logging.Logger;

public class KosuzuKnowsWhereYouLive {
    private final Logger logger;

    public KosuzuKnowsWhereYouLive(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();
    }

    public @Nullable String getCountryCode(Player player) {
        var address = player.getAddress();
        if (address == null) {
            return null;
        }

        return getCountryCode(address.getAddress().getHostAddress());
    }

    /**
     * Get the country code of an IP address
     * @param ip The IP address
     * @return The country code, or null if the IP address is invalid or location is unknown
     */
    public @Nullable String getCountryCode(String ip) {
        if (ip == null) {
            return null;
        }

        if (ip.equals("127.0.0.1")) {
            logger.warning("Localhost IP address detected from player; check forwarding on proxy?");
            return null;
        }

        //noinspection HttpUrlsUsage
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://ip-api.com/json/" + ip))
            .GET()
            .build();

        var client = HttpClient.newHttpClient();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body();
            var json = new Gson().fromJson(body, Properties.class);
            if (json.contains("countryCode")) {
                return json.getProperty("countryCode");
            }
            return null;
        } catch (IOException | InterruptedException e) {
            System.out.println("Failed to get country code for " + ip);
            return null;
        }
    }
}
