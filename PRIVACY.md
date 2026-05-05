# Confidentialité

CanoPaname est une application 100 % locale. Tout ce que tu fais — captures, photos, progression, badges — reste sur ton téléphone.

## Ce qui est stocké, et où

- **Captures** (espèce, position, date) : base SQLite privée à l'app.
- **Photos** : `Android/data/app.arbre/files/captures/`, supprimées à la désinstallation.
- **Préférences** : DataStore privé (onboarding vu, intro splash vue).

## Ce qui n'est jamais fait

- Aucun envoi vers un serveur, aucune télémétrie, aucun analytics.
- Aucun compte, aucune authentification.
- Aucun service Google requis (pensé pour GrapheneOS).

## Connexions réseau

- **Tuiles cartographiques** : `tiles.openfreemap.org` (OpenStreetMap, sans identifiant). C'est le seul appel réseau au runtime.
- Le dataset des arbres et les fiches Wikipedia FR sont **embarqués dans l'APK** au build, pas téléchargés.

## Permissions Android demandées

- **Position fine** : mesurer la distance à un arbre (< 30 m) pour autoriser la capture. Jamais envoyée.
- **Caméra** : photographier l'arbre capturé.
- **Vibrer** : retour haptique court à la capture.

## Sauvegarde

Tu peux exporter toutes tes captures en ZIP via Profil → Sauvegarde. Le fichier vit sur ton stockage choisi via Storage Access Framework. Le ZIP n'est pas chiffré — choisis un cloud chiffré (Cryptomator, Tresorit) si tu veux protéger ce backup.

## Contact

Question ou bug : ouvre une issue sur le repo GitHub, ou écris à `canopaname@pm.me`.
